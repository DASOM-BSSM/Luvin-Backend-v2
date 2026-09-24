#!/usr/bin/env bash
set -euo pipefail

: "${ECS_CLUSTER:?ECS_CLUSTER is required}"
: "${ECS_SERVICE:?ECS_SERVICE is required}"
: "${CONTAINER_NAME:?CONTAINER_NAME is required}"
: "${IMAGE_URI:?IMAGE_URI is required}"

# 마이그레이션 도구(Flyway/Liquibase 등)가 아직 없다. 지금은
# spring.jpa.hibernate.ddl-auto=update가 앱 기동 시 스키마를 자동으로 맞춘다 — 별도의
# 실패 격리된 마이그레이션 단계가 없다는 뜻이다. 자세한 원칙은 docs/DEPLOYMENT.md 참고.
# 마이그레이션 도구가 추가되면, 서비스 갱신 전에 여기서 일회성 ECS 태스크로 실행하고
# 실패 시 exit 1로 즉시 중단해야 한다(AI 저장소 infra/deploy/deploy-ai-staging.sh의
# register_with_image + run-task + wait tasks-stopped + exit code 확인 패턴 참고).

register_with_image() {
  local task_definition definition
  task_definition="$(aws ecs describe-services --cluster "$ECS_CLUSTER" --services "$ECS_SERVICE" \
    --query 'services[0].taskDefinition' --output text)"
  test "$task_definition" != None
  definition="$(aws ecs describe-task-definition --task-definition "$task_definition" --query taskDefinition --output json)"
  jq -e --arg container "$CONTAINER_NAME" \
    '(.containerDefinitions | any(.name == $container)) and ((.containerDefinitions | length) == 1)' \
    <<< "$definition" >/dev/null
  jq --arg container "$CONTAINER_NAME" --arg image "$IMAGE_URI" \
    '{family, taskRoleArn, executionRoleArn, networkMode, containerDefinitions, volumes,
      placementConstraints, requiresCompatibilities, cpu, memory, runtimePlatform,
      ephemeralStorage, pidMode, ipcMode, proxyConfiguration, inferenceAccelerators}
     | with_entries(select(.value != null))
     | .containerDefinitions |= map(if .name == $container then .image = $image else . end)' \
    <<< "$definition" > task-definition.json
  aws ecs register-task-definition --cli-input-json file://task-definition.json \
    --query 'taskDefinition.taskDefinitionArn' --output text
}

new_task_definition="$(register_with_image)"
echo "Registered $new_task_definition"

aws ecs update-service --cluster "$ECS_CLUSTER" --service "$ECS_SERVICE" \
  --task-definition "$new_task_definition" --query 'service.taskDefinition' --output text

aws ecs wait services-stable --cluster "$ECS_CLUSTER" --services "$ECS_SERVICE"

actual="$(aws ecs describe-services --cluster "$ECS_CLUSTER" --services "$ECS_SERVICE" \
  --query 'services[0].taskDefinition' --output text)"
if [[ "$actual" != "$new_task_definition" ]]; then
  echo "Unexpected task definition for $ECS_SERVICE: $actual (expected $new_task_definition; deployment circuit breaker likely rolled back)" >&2
  exit 1
fi

# ecs wait services-stable이 성공해도 ALB 쪽 헬스체크 반영에 약간의 지연이 있을 수 있어
# 명시적으로 한 번 더 확인한다.
target_group_arn="$(aws elbv2 describe-target-groups --names "$ECS_SERVICE" \
  --query 'TargetGroups[0].TargetGroupArn' --output text)"
test "$target_group_arn" != None

healthy=false
states=""
for _ in $(seq 1 15); do
  states="$(aws elbv2 describe-target-health --target-group-arn "$target_group_arn" \
    --query 'TargetHealthDescriptions[].TargetHealth.State' --output text)"
  if [[ -n "$states" ]]; then
    all_healthy=true
    for state in $states; do
      if [[ "$state" != "healthy" ]]; then
        all_healthy=false
      fi
    done
    if [[ "$all_healthy" == true ]]; then
      healthy=true
      break
    fi
  fi
  sleep 10
done

if [[ "$healthy" != true ]]; then
  echo "ALB target group $target_group_arn did not reach healthy state: $states" >&2
  exit 1
fi

echo "Deployed $new_task_definition to $ECS_SERVICE; ALB targets healthy."
