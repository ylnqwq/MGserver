# -*- coding: utf-8 -*-

import numpy as np

from multi_objective.mo_utils import (
    best_sum_history_value,
    evaluate_objectives,
    greedy_select_multi,
    population_scores,
    reinitialize_source,
    select_partner,
    update_archive,
    validate_bounds,
)


def good_point_set(food_number, dimension):
    r = 2 * np.cos(2 * np.pi * np.arange(1, dimension + 1) / 7)
    indexes = np.arange(1, food_number + 1).reshape(-1, 1)
    return np.mod(indexes * r, 1.0)


def initialize_food_sources(food_number, bounds, objective_function):
    bounds = validate_bounds(bounds)
    lower_bounds = bounds[:, 0]
    upper_bounds = bounds[:, 1]
    dimension = len(bounds)

    points = good_point_set(food_number, dimension)
    food_sources = lower_bounds + points * (upper_bounds - lower_bounds)
    objectives = evaluate_objectives(objective_function, food_sources)
    trials = np.zeros(food_number, dtype=int)
    return food_sources, objectives, trials


def create_neighbor(food_sources, index, bounds, archive_solutions=None, archive_guidance_rate=0.4):
    food_number, dimension = food_sources.shape
    neighbor = food_sources[index].copy()

    parameter_index = np.random.randint(dimension)
    partner_index = select_partner(food_number, index)

    phi = np.random.uniform(-1.0, 1.0)
    neighbor[parameter_index] = (
        food_sources[index, parameter_index]
        + phi * (food_sources[index, parameter_index] - food_sources[partner_index, parameter_index])
    )

    if archive_solutions is not None and len(archive_solutions) > 0 and archive_guidance_rate > 0:
        archive_index = np.random.randint(len(archive_solutions))
        guide = archive_solutions[archive_index]
        beta = np.random.uniform(0.0, archive_guidance_rate)
        neighbor[parameter_index] += beta * (guide[parameter_index] - food_sources[index, parameter_index])

    bounds = np.asarray(bounds, dtype=float)
    return np.clip(neighbor, bounds[:, 0], bounds[:, 1])


def employed_bee_phase(
    food_sources,
    objectives,
    trials,
    bounds,
    objective_function,
):
    for i in range(len(food_sources)):
        candidate = create_neighbor(food_sources, i, bounds)
        candidate_objective = objective_function(candidate)
        greedy_select_multi(food_sources, objectives, trials, i, candidate, candidate_objective)


def tournament_select(ranks, distances, tournament_size=3):
    food_number = len(ranks)
    tournament_size = min(tournament_size, food_number)
    candidates = np.random.choice(food_number, size=tournament_size, replace=False)

    best = candidates[0]
    for candidate in candidates[1:]:
        if ranks[candidate] < ranks[best]:
            best = candidate
        elif ranks[candidate] == ranks[best] and distances[candidate] > distances[best]:
            best = candidate
    return best


def onlooker_bee_phase(
    food_sources,
    objectives,
    trials,
    bounds,
    objective_function,
    tournament_size=3,
):
    ranks, distances = population_scores(objectives)

    for _ in range(len(food_sources)):
        selected_index = tournament_select(ranks, distances, tournament_size)
        candidate = create_neighbor(food_sources, selected_index, bounds)
        candidate_objective = objective_function(candidate)
        greedy_select_multi(food_sources, objectives, trials, selected_index, candidate, candidate_objective)


def scout_bee_phase(food_sources, objectives, trials, bounds, objective_function, limit):
    bounds = np.asarray(bounds, dtype=float)
    lower_bounds = bounds[:, 0]
    upper_bounds = bounds[:, 1]
    for i in range(len(food_sources)):
        if trials[i] >= limit:
            reinitialize_source(food_sources, objectives, trials, i, lower_bounds, upper_bounds, objective_function)


def elite_enhancement_phase(
    food_sources,
    objectives,
    trials,
    bounds,
    objective_function,
    elite_rate=0.25,
    archive_solutions=None,
    archive_guidance_rate=0.4,
):
    food_number = len(food_sources)
    elite_number = max(1, int(np.ceil(food_number * elite_rate)))
    ranks, distances = population_scores(objectives)
    order = np.lexsort((-distances, ranks))
    elite_indexes = order[:elite_number]

    for index in elite_indexes:
        candidate = create_neighbor(food_sources, index, bounds, archive_solutions, archive_guidance_rate)
        candidate_objective = objective_function(candidate)
        greedy_select_multi(
            food_sources,
            objectives,
            trials,
            index,
            candidate,
            candidate_objective,
            count_failure=False,
        )


def worst_elimination_phase(food_sources, objectives, trials, bounds, objective_function, elimination_rate=0.25):
    food_number = len(food_sources)
    elimination_number = int(np.ceil(food_number * elimination_rate))
    if elimination_number <= 0:
        return

    ranks, distances = population_scores(objectives)
    finite_distances = np.where(np.isfinite(distances), distances, np.finfo(float).max)
    order = np.lexsort((finite_distances, -ranks))
    worst_indexes = order[:elimination_number]

    bounds = np.asarray(bounds, dtype=float)
    lower_bounds = bounds[:, 0]
    upper_bounds = bounds[:, 1]

    for index in worst_indexes:
        reinitialize_source(food_sources, objectives, trials, index, lower_bounds, upper_bounds, objective_function)


def get_current_elimination_rate(initial_rate, iteration, max_iter, initial_best_value=None, current_best_value=None):
    if initial_rate <= 0:
        return 0.0

    time_progress = (iteration + 1) / max(1, max_iter)
    improvement_progress = 0.0
    if initial_best_value is not None and current_best_value is not None:
        if np.isfinite(initial_best_value) and np.isfinite(current_best_value):
            denominator = max(abs(initial_best_value), 1e-12)
            improvement_progress = (initial_best_value - current_best_value) / denominator

    progress = max(time_progress, improvement_progress)
    progress = min(1.0, max(0.0, progress))
    return initial_rate * progress


def multi_objective_iabc(
    objective_function,
    bounds,
    bee=80,
    max_iter=800,
    limit=160,
    tournament_size=3,
    elite_rate=0.25,
    elimination_rate=0.25,
    archive_size=100,
    archive_guidance_rate=0.4,
    seed=None,
):
    if seed is None:
        used_seed = int(np.random.SeedSequence().generate_state(1)[0])
    else:
        used_seed = int(seed)
    np.random.seed(used_seed)

    bounds = validate_bounds(bounds)
    food_sources, objectives, trials = initialize_food_sources(bee, bounds, objective_function)
    archive_solutions = np.empty((0, len(bounds)), dtype=float)
    archive_objectives = np.empty((0, objectives.shape[1]), dtype=float)
    archive_solutions, archive_objectives = update_archive(
        archive_solutions,
        archive_objectives,
        food_sources,
        objectives,
        archive_size,
    )

    history = [best_sum_history_value(archive_objectives)]

    for iteration in range(max_iter):
        employed_bee_phase(
            food_sources,
            objectives,
            trials,
            bounds,
            objective_function,
        )
        onlooker_bee_phase(
            food_sources,
            objectives,
            trials,
            bounds,
            objective_function,
            tournament_size=tournament_size,
        )
        scout_bee_phase(food_sources, objectives, trials, bounds, objective_function, limit)
        elite_enhancement_phase(
            food_sources,
            objectives,
            trials,
            bounds,
            objective_function,
            elite_rate=elite_rate,
            archive_solutions=archive_solutions,
            archive_guidance_rate=archive_guidance_rate,
        )
        population_best_value = float(np.min(np.sum(objectives, axis=1)))
        current_best_value = min(history[-1], population_best_value)
        current_elimination_rate = get_current_elimination_rate(
            elimination_rate,
            iteration,
            max_iter,
            initial_best_value=history[0],
            current_best_value=current_best_value,
        )
        worst_elimination_phase(
            food_sources,
            objectives,
            trials,
            bounds,
            objective_function,
            elimination_rate=current_elimination_rate,
        )

        archive_solutions, archive_objectives = update_archive(
            archive_solutions,
            archive_objectives,
            food_sources,
            objectives,
            archive_size,
        )
        history.append(best_sum_history_value(archive_objectives))
        print(f"MG_PROGRESS={iteration + 1}/{max_iter}", flush=True)

    return archive_solutions, archive_objectives, history, used_seed

