# -*- coding: utf-8 -*-

import numpy as np


def validate_bounds(bounds):
    bounds = np.asarray(bounds, dtype=float)
    if bounds.ndim != 2 or bounds.shape[1] != 2:
        raise ValueError("bounds 必须是形如 [(lower, upper), ...] 的二维数组或列表")
    if np.any(bounds[:, 0] >= bounds[:, 1]):
        raise ValueError("每个变量的下界必须小于上界")
    return bounds


def evaluate_objectives(objective_function, solutions):
    return np.array([objective_function(solution) for solution in solutions], dtype=float)


def select_partner(item_count, current_index):
    partner_index = np.random.randint(item_count)
    while partner_index == current_index:
        partner_index = np.random.randint(item_count)
    return partner_index


def initialize_random_sources(food_number, bounds, objective_function):
    bounds = validate_bounds(bounds)
    lower_bounds = bounds[:, 0]
    upper_bounds = bounds[:, 1]
    food_sources = np.random.uniform(lower_bounds, upper_bounds, size=(food_number, len(bounds)))
    objectives = evaluate_objectives(objective_function, food_sources)
    trials = np.zeros(food_number, dtype=int)
    return food_sources, objectives, trials


def reinitialize_source(sources, objectives, trials, index, lower_bounds, upper_bounds, objective_function):
    sources[index] = np.random.uniform(lower_bounds, upper_bounds)
    objectives[index] = objective_function(sources[index])
    trials[index] = 0


def dominates(left, right):
    left = np.asarray(left, dtype=float)
    right = np.asarray(right, dtype=float)
    return np.all(left <= right) and np.any(left < right)


def non_dominated_mask(objectives):
    objectives = np.asarray(objectives, dtype=float)
    mask = np.ones(len(objectives), dtype=bool)

    for i in range(len(objectives)):
        if not mask[i]:
            continue
        for j in range(len(objectives)):
            if i != j and dominates(objectives[j], objectives[i]):
                mask[i] = False
                break

    return mask


def pareto_rank(objectives):
    objectives = np.asarray(objectives, dtype=float)
    remaining = np.arange(len(objectives))
    ranks = np.zeros(len(objectives), dtype=int)
    rank = 0

    while len(remaining) > 0:
        front_mask = non_dominated_mask(objectives[remaining])
        front = remaining[front_mask]
        ranks[front] = rank
        remaining = remaining[~front_mask]
        rank += 1

    return ranks


def crowding_distance(objectives):
    objectives = np.asarray(objectives, dtype=float)
    count = len(objectives)
    if count == 0:
        return np.array([], dtype=float)
    if count <= 2:
        return np.full(count, np.inf)

    distance = np.zeros(count, dtype=float)
    objective_count = objectives.shape[1]

    for objective_index in range(objective_count):
        order = np.argsort(objectives[:, objective_index])
        ordered_values = objectives[order, objective_index]
        span = ordered_values[-1] - ordered_values[0]

        distance[order[0]] = np.inf
        distance[order[-1]] = np.inf
        if np.isclose(span, 0.0):
            continue

        for position in range(1, count - 1):
            previous_value = ordered_values[position - 1]
            next_value = ordered_values[position + 1]
            distance[order[position]] += (next_value - previous_value) / span

    return distance


def truncate_by_crowding(solutions, objectives, max_size):
    if len(solutions) <= max_size:
        return solutions, objectives

    distance = crowding_distance(objectives)
    finite_distance = np.where(np.isfinite(distance), distance, np.finfo(float).max)
    selected = np.argsort(-finite_distance)[:max_size]
    return solutions[selected], objectives[selected]


def update_archive(archive_solutions, archive_objectives, candidate_solutions, candidate_objectives, max_size):
    candidate_solutions = np.asarray(candidate_solutions, dtype=float)
    candidate_objectives = np.asarray(candidate_objectives, dtype=float)

    if len(archive_solutions) == 0:
        all_solutions = candidate_solutions
        all_objectives = candidate_objectives
    else:
        all_solutions = np.vstack([archive_solutions, candidate_solutions])
        all_objectives = np.vstack([archive_objectives, candidate_objectives])

    _, unique_indexes = np.unique(all_objectives, axis=0, return_index=True)
    unique_indexes = np.sort(unique_indexes)
    all_solutions = all_solutions[unique_indexes]
    all_objectives = all_objectives[unique_indexes]

    mask = non_dominated_mask(all_objectives)
    archive_solutions = all_solutions[mask]
    archive_objectives = all_objectives[mask]
    return truncate_by_crowding(archive_solutions, archive_objectives, max_size)


def population_scores(objectives):
    ranks = pareto_rank(objectives)
    distances = np.zeros(len(objectives), dtype=float)
    for rank in np.unique(ranks):
        indexes = np.where(ranks == rank)[0]
        distances[indexes] = crowding_distance(objectives[indexes])
    return ranks, distances


def greedy_select_multi(
    solutions,
    objectives,
    trials,
    index,
    candidate_solution,
    candidate_objective,
    count_failure=True,
):
    current_objective = objectives[index]

    if dominates(candidate_objective, current_objective):
        solutions[index] = candidate_solution
        objectives[index] = candidate_objective
        trials[index] = 0
        return

    if dominates(current_objective, candidate_objective):
        if count_failure:
            trials[index] += 1
        return

    if np.random.rand() < 0.5:
        solutions[index] = candidate_solution
        objectives[index] = candidate_objective
        trials[index] = 0
    elif count_failure:
        trials[index] += 1


def spacing_metric(objectives):
    objectives = np.asarray(objectives, dtype=float)
    if len(objectives) <= 2:
        return 0.0

    distances = []
    for i in range(len(objectives)):
        diff = objectives - objectives[i]
        norm = np.linalg.norm(diff, axis=1)
        norm[i] = np.inf
        distances.append(np.min(norm))

    distances = np.asarray(distances, dtype=float)
    return float(np.std(distances, ddof=1))


def igd_metric(approximation_front, reference_front):
    approximation_front = np.asarray(approximation_front, dtype=float)
    reference_front = np.asarray(reference_front, dtype=float)
    if len(approximation_front) == 0 or len(reference_front) == 0:
        return np.inf

    distances = []
    for reference_point in reference_front:
        diff = approximation_front - reference_point
        distances.append(np.min(np.linalg.norm(diff, axis=1)))
    return float(np.mean(distances))


def igd_plus_metric(approximation_front, reference_front):
    approximation_front = np.asarray(approximation_front, dtype=float)
    reference_front = np.asarray(reference_front, dtype=float)
    if len(approximation_front) == 0 or len(reference_front) == 0:
        return np.inf

    distances = []
    for reference_point in reference_front:
        diff = np.maximum(approximation_front - reference_point, 0.0)
        distances.append(np.min(np.linalg.norm(diff, axis=1)))
    return float(np.mean(distances))


def nondominated_front_2d(objectives):
    order = np.lexsort((objectives[:, 1], objectives[:, 0]))
    sorted_points = objectives[order]
    selected = []
    best_second = np.inf
    for point in sorted_points:
        if point[1] < best_second - 1e-12:
            selected.append(point)
            best_second = point[1]
    return np.asarray(selected, dtype=float)


def select_reference_candidates(objectives, max_candidates):
    if max_candidates <= 0 or len(objectives) <= max_candidates:
        return objectives

    selected = set(np.linspace(0, len(objectives) - 1, max_candidates, dtype=int).tolist())
    edge_count = max(20, max_candidates // (objectives.shape[1] * 20))
    for axis in range(objectives.shape[1]):
        order = np.argsort(objectives[:, axis])
        selected.update(order[:edge_count].tolist())
        selected.update(order[-edge_count:].tolist())
    indexes = np.fromiter(sorted(selected), dtype=int)
    return objectives[indexes]


def nondominated_front_3d(objectives):
    order = np.lexsort((objectives[:, 2], objectives[:, 1], objectives[:, 0]))
    front = np.empty((0, 3), dtype=float)

    for point in objectives[order]:
        if len(front) > 0:
            dominated_by_front = np.any(np.all(front <= point, axis=1) & np.any(front < point, axis=1))
            if dominated_by_front:
                continue
            dominated_front = np.all(point <= front, axis=1) & np.any(point < front, axis=1)
            if np.any(dominated_front):
                front = front[~dominated_front]
        front = np.vstack([front, point])
    return front


def nondominated_front_fast(objectives, max_candidates=12000):
    objectives = np.asarray(objectives, dtype=float)
    if objectives.shape[1] == 2:
        return nondominated_front_2d(objectives)
    if objectives.shape[1] == 3:
        candidates = select_reference_candidates(objectives, max_candidates)
        return nondominated_front_3d(candidates)
    return objectives[non_dominated_mask(objectives)]


def normalize_objectives(objectives, ideal_point, nadir_point):
    span = nadir_point - ideal_point
    span = np.where(np.isclose(span, 0.0), 1.0, span)
    return (objectives - ideal_point) / span


def select_reference_points(reference_front, max_points):
    if max_points <= 0 or len(reference_front) <= max_points:
        return reference_front
    indexes = np.linspace(0, len(reference_front) - 1, max_points).astype(int)
    return reference_front[indexes]


def mean_min_distance(approximation_front, reference_front, plus=False, chunk_size=256):
    if len(approximation_front) == 0 or len(reference_front) == 0:
        return np.inf

    min_distances = []
    for start in range(0, len(reference_front), chunk_size):
        reference_chunk = reference_front[start : start + chunk_size]
        diff = approximation_front[None, :, :] - reference_chunk[:, None, :]
        if plus:
            diff = np.maximum(diff, 0.0)
        distances = np.linalg.norm(diff, axis=2)
        min_distances.append(np.min(distances, axis=1))
    return float(np.mean(np.concatenate(min_distances)))


def attach_igd_metrics(grouped_results, reference_points=2000, reference_candidates=12000):
    all_objectives = np.vstack(
        [item["archive_objectives"] for results in grouped_results.values() for item in results]
    )
    _, unique_indexes = np.unique(all_objectives, axis=0, return_index=True)
    all_objectives = all_objectives[np.sort(unique_indexes)]

    reference_front = nondominated_front_fast(all_objectives, max_candidates=reference_candidates)
    reference_front = reference_front[
        np.lexsort(tuple(reference_front[:, index] for index in range(reference_front.shape[1] - 1, -1, -1)))
    ]
    used_reference_front = select_reference_points(reference_front, reference_points)

    ideal_point = np.min(all_objectives, axis=0)
    nadir_point = np.max(all_objectives, axis=0)
    normalized_reference = normalize_objectives(used_reference_front, ideal_point, nadir_point)

    for results in grouped_results.values():
        for item in results:
            normalized_objectives = normalize_objectives(item["archive_objectives"], ideal_point, nadir_point)
            item["reference_front_size"] = len(reference_front)
            item["used_reference_front_size"] = len(used_reference_front)
            item["igd"] = mean_min_distance(normalized_objectives, normalized_reference)
            item["igd_plus"] = mean_min_distance(normalized_objectives, normalized_reference, plus=True)


def best_sum_history_value(archive_objectives):
    if len(archive_objectives) == 0:
        return np.inf
    return float(np.min(np.sum(archive_objectives, axis=1)))


def two_objective_hypervolume(objectives, reference_point):
    objectives = np.asarray(objectives, dtype=float)
    reference_point = np.asarray(reference_point, dtype=float)
    valid_mask = np.all(objectives < reference_point, axis=1)
    points = objectives[valid_mask]
    if len(points) == 0:
        return 0.0

    points = points[non_dominated_mask(points)]
    points = points[np.argsort(points[:, 0])]

    hypervolume = 0.0
    for index, point in enumerate(points):
        next_x = points[index + 1, 0] if index + 1 < len(points) else reference_point[0]
        width = max(0.0, next_x - point[0])
        height = max(0.0, reference_point[1] - point[1])
        hypervolume += width * height
    return float(hypervolume)


def three_objective_hypervolume(objectives, reference_point):
    objectives = np.asarray(objectives, dtype=float)
    reference_point = np.asarray(reference_point, dtype=float)
    valid_mask = np.all(objectives < reference_point, axis=1)
    points = objectives[valid_mask]
    if len(points) == 0:
        return 0.0

    points = points[non_dominated_mask(points)]
    points = points[np.argsort(points[:, 0])]

    hypervolume = 0.0
    for index, point in enumerate(points):
        next_x = points[index + 1, 0] if index + 1 < len(points) else reference_point[0]
        width = max(0.0, next_x - point[0])
        if width <= 0.0:
            continue
        slice_points = points[: index + 1, 1:3]
        hypervolume += width * two_objective_hypervolume(slice_points, reference_point[1:3])
    return float(hypervolume)


def calculate_hypervolume(objectives, reference_point):
    objective_count = np.asarray(objectives).shape[1]
    if objective_count == 2:
        return two_objective_hypervolume(objectives, reference_point)
    if objective_count == 3:
        return three_objective_hypervolume(objectives, reference_point)
    raise ValueError(f"暂不支持 {objective_count} 目标超体积计算")
