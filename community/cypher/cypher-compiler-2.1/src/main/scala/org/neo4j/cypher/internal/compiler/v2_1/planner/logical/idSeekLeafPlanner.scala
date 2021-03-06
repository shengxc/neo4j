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

import org.neo4j.cypher.internal.compiler.v2_1.ast._
import org.neo4j.cypher.internal.compiler.v2_1.planner.logical.plans._
import org.neo4j.cypher.InternalException
import org.neo4j.graphdb.Direction

case class idSeekLeafPlanner(predicates: Seq[Expression]) extends LeafPlanner {
  def apply()(implicit context: LogicalPlanContext): Seq[LogicalPlan] = {
    predicates.collect {
      // MATCH (a)-[r]->b WHERE id(r) = value
      case predicate@Equals(FunctionInvocation(FunctionName("id"), _, IndexedSeq(idExpr)), ConstantExpression(idValueExpr)) =>
        (predicate, idExpr, Seq(idValueExpr))

      // MATCH (a)-[r]->b WHERE id(r) IN value
      case predicate@In(FunctionInvocation(FunctionName("id"), _, IndexedSeq(idExpr)), idsExpr@Collection(idValueExprs))
        if idValueExprs.forall(ConstantExpression.unapply(_).isDefined) =>
        (predicate, idExpr, idValueExprs)
    }.collect {
      case (predicate, RelationshipIdName(idName), idValues) =>
        context.queryGraph.patternRelationships.filter(_.name == idName).collectFirst {
          case PatternRelationship(relName, (l, r), Direction.BOTH, types) =>
            createUndirectedRelationshipByIdSeek(relName, l, r, types, idValues, predicate)

          case PatternRelationship(relName, (l, r), dir, types)  =>
            createDirectedRelationshipByIdSeek(idName, l, r, dir, types, idValues, predicate)
        }.getOrElse(failIfNotFound(idName.name))

      case (predicate, NodeIdName(idName), idValues) =>
        NodeByIdSeek(idName, idValues)(Seq(predicate))
    }
  }

  private def failIfNotFound(idName: String): Nothing = {
    throw new InternalException(s"Identifier ${idName} typed as a relationship, but no relationship found by that name in the query graph ")
  }

  private def createDirectedRelationshipByIdSeek(relName: IdName, l: IdName, r: IdName, dir: Direction, types: Seq[RelTypeName], idExpr: Seq[Expression], predicate: Expression)
                                                (implicit context: LogicalPlanContext) = {
    val (from, to) = if (dir == Direction.OUTGOING) (l, r) else (r, l)
    val relById = DirectedRelationshipByIdSeek(relName, idExpr, from, to)(Seq(predicate))
    filterIfNeeded(relById, relName.name, types)
  }

  private def createUndirectedRelationshipByIdSeek(relName: IdName, l: IdName, r: IdName, types: Seq[RelTypeName], idExpr: Seq[Expression], predicate: Expression)
                                                  (implicit context: LogicalPlanContext) = {
    val relById = UndirectedRelationshipByIdSeek(relName, idExpr, l, r)(Seq(predicate))
    filterIfNeeded(relById, relName.name, types)

  }

  private def filterIfNeeded(plan: LogicalPlan, relName: String, types: Seq[RelTypeName])
                            (implicit context: LogicalPlanContext): LogicalPlan =
    if (types.isEmpty)
      plan
    else {
      val id = Identifier(relName)(null)
      val name = FunctionName("type")(null)
      val invocation = FunctionInvocation(name, id)(null)

      val predicates: Seq[Expression] = types.map {
        relType => Equals(invocation, StringLiteral(relType.name)(null))(null)
      }

      val predicate = predicates.reduce(Or(_, _)(null))
      Selection(Seq(predicate), plan)
    }
}
