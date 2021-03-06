/**
 * Copyright (c) 2002-2014 "Neo Technology,"
 * Network Engine for Objects in Lund AB [http://neotechnology.com]
 *
 * This file is part of Neo4j.
 *
 * Neo4j is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.neo4j.cypher.internal.compiler.v2_1.planner.logical

import org.neo4j.cypher.internal.compiler.v2_1.planner.logical.plans.LogicalPlan
import org.neo4j.cypher.internal.compiler.v2_1.ast.Expression
import Metrics._

// Helper class mainly used via LogicalPlanningTestSupport
case class OverridableMetricsFactory(
  metricsFactory: MetricsFactory,
  altNewSelectivityEstimator: Option[() => SelectivityEstimator] = None,
  altNewCardinalityEstimator: Option[(SelectivityEstimator) => CardinalityEstimator] = None,
  altNewCostModel: Option[(CardinalityEstimator) => CostModel] = None) extends MetricsFactory {

  def newCostModel(cardinality: CardinalityEstimator): CostModel =
    altNewCostModel.getOrElse(metricsFactory.newCostModel(_))(cardinality)

  def newCardinalityEstimator(selectivity: SelectivityEstimator): CardinalityEstimator =
    altNewCardinalityEstimator.getOrElse(metricsFactory.newCardinalityEstimator(_))(selectivity)

  def newSelectivityEstimator: SelectivityEstimator =
    altNewSelectivityEstimator.getOrElse(() => metricsFactory.newSelectivityEstimator)()

  def replaceCostModel(pf: PartialFunction[LogicalPlan, Int]) =
    copy(altNewCostModel = Some((_: CardinalityEstimator) => pf.lift.andThen(_.getOrElse(Int.MaxValue))))

  def replaceCardinalityEstimator(pf: PartialFunction[LogicalPlan, Int]) =
    copy(altNewCardinalityEstimator = Some((_: SelectivityEstimator) => pf.lift.andThen(_.getOrElse(Int.MaxValue))))

  def amendCardinalityEstimator(pf: PartialFunction[LogicalPlan, Int]) =
    copy(altNewCardinalityEstimator = Some({ (selectivity: SelectivityEstimator) =>
      val fallback: PartialFunction[LogicalPlan, Int] = {
        case plan => metricsFactory.newCardinalityEstimator(selectivity)(plan)
      }
      (pf `orElse` fallback).lift.andThen(_.getOrElse(Int.MaxValue))
    }))

  def replaceSelectivityEstimator(pf: PartialFunction[Expression, Double]) =
    copy(altNewSelectivityEstimator = Some(() => pf.lift.andThen(_.getOrElse(1.0d))))
}
