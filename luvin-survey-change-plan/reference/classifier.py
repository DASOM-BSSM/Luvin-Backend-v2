"""Deterministic reference implementation; not a validated psychological test.
Run: python3 classifier.py survey_config.json [answers.json]
answers.json: {"survey_version": "...", "answers": {"q01": "q01_a1", ...}}
Without answers.json, uses the middle answer in each question as a synthetic example.
"""
from __future__ import annotations

import json
import sys
from decimal import Decimal, ROUND_HALF_UP, localcontext
from fractions import Fraction
from pathlib import Path


def display(value: Fraction | Decimal, places: int = 2) -> float:
    with localcontext() as ctx:
        ctx.prec = 40
        number = (Decimal(value.numerator) / Decimal(value.denominator)
                  if isinstance(value, Fraction) else value)
        return float(number.quantize(Decimal(1).scaleb(-places), rounding=ROUND_HALF_UP))


def validate_config(config: dict) -> None:
    dims = config['dimensions']
    questions = config['questions']
    assert len(dims) == 20
    assert sum(x['kind'] == 'core' for x in dims.values()) == 13
    assert len(questions) == 20
    assert len({q['id'] for q in questions}) == 20
    assert {q['primary_dimension'] for q in questions} == set(dims)
    ids = []
    for question in questions:
        assert len(question['answers']) == 3
        for answer in question['answers']:
            ids.append(answer['id'])
            assert answer['evidence'][question['primary_dimension']]['weight'] == 2
            for key, ev in answer['evidence'].items():
                assert key in dims
                assert ev['value'] in (-1, 0, 1)
                assert type(ev['weight']) is int and ev['weight'] > 0
    assert len(ids) == len(set(ids))
    assert config['prior_weight'] > 0
    assert len(config['profiles']) == 8
    assert len(set(config['tie_break_order'])) == 8
    assert set(config['tie_break_order']) == {p['id'] for p in config['profiles']}
    for profile in config['profiles']:
        assert profile['features']
        for key, feature in profile['features'].items():
            low, high = feature['range']
            assert key in dims and 1 <= low <= high <= 100
            assert feature['weight'] > 0


def classify(config: dict, request: dict) -> dict:
    if request.get('survey_version') != config['survey_version']:
        raise ValueError('SURVEY_VERSION_MISMATCH')
    answers = request.get('answers')
    if not isinstance(answers, dict):
        raise ValueError('INVALID_ANSWERS')
    if set(answers) != {q['id'] for q in config['questions']}:
        raise ValueError('MISSING_OR_UNKNOWN_QUESTION')

    observed = {key: [] for key in config['dimensions']}
    for question in config['questions']:
        answer = next((a for a in question['answers']
                       if a['id'] == answers[question['id']]), None)
        if answer is None:
            raise ValueError('INVALID_ANSWER')
        for key, evidence in answer['evidence'].items():
            observed[key].append({**evidence, 'question_id': question['id'],
                                  'answer_id': answer['id']})

    exact_scores = {}
    score_output = {}
    for key, evidence in observed.items():
        weight = sum(e['weight'] for e in evidence)
        weighted_sum = sum(e['weight'] * e['value'] for e in evidence)
        if not weight:
            raise ValueError('UNOBSERVED_DIMENSION')
        score = Fraction(101, 2) + Fraction(99, 2) * Fraction(
            weighted_sum, config['prior_weight'] + weight)
        exact_scores[key] = score
        signs = {e['value'] for e in evidence}
        score_output[key] = {
            'score': display(score), 'evidence_weight': weight,
            'observations': len(evidence),
            'direction_conflict': 1 in signs and -1 in signs,
        }

    distances = {}
    rank_keys = {}
    with localcontext() as ctx:
        ctx.prec = 40
        for profile in config['profiles']:
            numerator = Fraction(0)
            denominator = 0
            for key, feature in profile['features'].items():
                low, high = feature['range']
                d = max(low - exact_scores[key], Fraction(0), exact_scores[key] - high)
                numerator += feature['weight'] * d * d
                denominator += feature['weight']
            square = numerator / denominator
            distance = (Decimal(square.numerator) / Decimal(square.denominator)).sqrt()
            distances[profile['id']] = distance
            rank_keys[profile['id']] = distance.quantize(
                Decimal('0.000001'), rounding=ROUND_HALF_UP)
        priority = {key: idx for idx, key in enumerate(config['tie_break_order'])}
        ordered = sorted(distances, key=lambda key: (rank_keys[key], priority[key]))
        first, second = ordered[:2]
        gap = max(Decimal(0), distances[second] - distances[first])
        tied = rank_keys[first] == rank_keys[second]

    winner = next(p for p in config['profiles'] if p['id'] == first)
    reasons = []
    center = Fraction(101, 2)
    for key, feature in winner['features'].items():
        low, high = feature['range']
        direction = 1 if low > center else -1 if high < center else 0
        aligned = (exact_scores[key] - center) * direction
        if aligned <= 5:
            continue
        source_ids = [e['answer_id'] for e in observed[key] if e['value'] == direction]
        if not source_ids:
            continue
        reasons.append((feature['weight'] * aligned, key, direction, source_ids))
    reasons.sort(key=lambda item: (-item[0], item[1]))

    return {
        'survey_version': config['survey_version'],
        'scoring_version': config['scoring_version'],
        'classification_version': config['classification_version'],
        'primary_type': first,
        'secondary_type': second,
        'mixed': gap < Decimal(str(config['distance_gap_threshold'])),
        'poor_fit': distances[first] > Decimal(str(config['poor_fit_distance_threshold'])),
        'tie': tied,
        'top_two_distance_gap': display(gap, 6),
        'distances': {key: display(distances[key], 6) for key in ordered},
        'core_scores': {key: val for key, val in score_output.items()
                        if config['dimensions'][key]['kind'] == 'core'},
        'auxiliary_scores': {key: val for key, val in score_output.items()
                             if config['dimensions'][key]['kind'] == 'auxiliary'},
        'reason_evidence': [{'dimension': key, 'direction': direction,
                             'source_answer_ids': sources}
                            for _, key, direction, sources in reasons[:3]],
    }


def unique_object(pairs: list) -> dict:
    """Reject duplicate JSON keys before they can be silently overwritten."""
    result = {}
    for key, value in pairs:
        if key in result:
            raise ValueError('DUPLICATE_JSON_KEY')
        result[key] = value
    return result


def main() -> None:
    config = json.loads(Path(sys.argv[1]).read_text(), object_pairs_hook=unique_object)
    validate_config(config)
    request = (json.loads(Path(sys.argv[2]).read_text(), object_pairs_hook=unique_object)
               if len(sys.argv) > 2 else {
                   'survey_version': config['survey_version'],
                   'answers': {q['id']: q['answers'][1]['id'] for q in config['questions']},
               })
    print(json.dumps(classify(config, request), ensure_ascii=False, indent=2))


if __name__ == '__main__':
    main()
