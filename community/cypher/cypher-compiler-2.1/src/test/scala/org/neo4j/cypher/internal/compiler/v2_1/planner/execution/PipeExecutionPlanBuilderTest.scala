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
package org.neo4j.cypher.internal.compiler.v2_1.planner.execution

import org.neo4j.cypher.internal.commons.CypherFunSuite
import org.neo4j.cypher.internal.compiler.v2_1._
import org.neo4j.cypher.internal.compiler.v2_1.commands.{expressions => legacy}
import org.neo4j.cypher.internal.compiler.v2_1.ast.convert.ExpressionConverters._
import org.neo4j.cypher.internal.compiler.v2_1.planner.LogicalPlanningTestSupport
import org.neo4j.cypher.internal.compiler.v2_1.pipes._
import org.neo4j.cypher.internal.compiler.v2_1.planner.logical.plans._
import org.neo4j.cypher.internal.compiler.v2_1.pipes.DirectedRelationshipByIdSeekPipe
import org.neo4j.cypher.internal.compiler.v2_1.pipes.ProjectionNewPipe
import org.neo4j.cypher.internal.compiler.v2_1.planner.logical.plans.SingleRow
import org.neo4j.cypher.internal.compiler.v2_1.planner.logical.plans.IdName
import org.neo4j.cypher.internal.compiler.v2_1.planner.logical.plans.DirectedRelationshipByIdSeek
import org.neo4j.cypher.internal.compiler.v2_1.planner.logical.plans.Projection
import org.neo4j.cypher.internal.compiler.v2_1.pipes.NodeByLabelScanPipe
import org.neo4j.cypher.internal.compiler.v2_1.ast.{Collection, SignedIntegerLiteral}
import org.neo4j.cypher.internal.compiler.v2_1.pipes.NullPipe
import org.neo4j.cypher.internal.compiler.v2_1.pipes.AllNodesScanPipe
import org.neo4j.cypher.internal.compiler.v2_1.LabelId
import org.neo4j.cypher.internal.compiler.v2_1.pipes.NodeByIdSeekPipe
import org.neo4j.cypher.internal.compiler.v2_1.planner.logical.plans.NodeByLabelScan
import org.neo4j.cypher.internal.compiler.v2_1.planner.logical.plans.NodeByIdSeek
import org.neo4j.cypher.internal.compiler.v2_1.pipes.CartesianProductPipe
import org.neo4j.cypher.internal.compiler.v2_1.planner.logical.plans.AllNodesScan
import org.neo4j.cypher.internal.compiler.v2_1.planner.logical.plans.CartesianProduct
import org.neo4j.graphdb.Direction

class PipeExecutionPlanBuilderTest extends CypherFunSuite with LogicalPlanningTestSupport {

  implicit val pipeMonitor = monitors.newMonitor[PipeMonitor]()
  implicit val context = newMockedLogicalPlanContext()

  val planBuilder = new PipeExecutionPlanBuilder(monitors)
  val pos = DummyPosition(0)

  test("projection only query") {
    val logicalPlan = Projection(SingleRow(), Map("42" -> SignedIntegerLiteral("42")(pos)))
    val pipeInfo = planBuilder.build(logicalPlan)

    pipeInfo should not be 'updating
    pipeInfo.periodicCommit should equal(None)
    pipeInfo.pipe should equal(ProjectionNewPipe(NullPipe(), Map("42" -> legacy.Literal(42))))
  }

  test("simple pattern query") {
    val logicalPlan = AllNodesScan(IdName("n"))
    val pipeInfo = planBuilder.build(logicalPlan)

    pipeInfo should not be 'updating
    pipeInfo.periodicCommit should equal(None)
    pipeInfo.pipe should equal(AllNodesScanPipe("n"))
  }

  test("simple label scan query") {
    val logicalPlan = NodeByLabelScan(IdName("n"), Right(LabelId(12)))(Seq.empty)
    val pipeInfo = planBuilder.build(logicalPlan)

    pipeInfo should not be 'updating
    pipeInfo.periodicCommit should equal(None)
    pipeInfo.pipe should equal(NodeByLabelScanPipe("n", Right(LabelId(12))))
  }

  test("simple node by id seek query") {
    val astLiteral = SignedIntegerLiteral("42")(pos)
    val logicalPlan = NodeByIdSeek(IdName("n"), Seq(astLiteral))(Seq.empty)
    val pipeInfo = planBuilder.build(logicalPlan)

    pipeInfo should not be 'updating
    pipeInfo.periodicCommit should equal(None)
    pipeInfo.pipe should equal(NodeByIdSeekPipe("n", Seq(astLiteral.asCommandExpression)))
  }

  test("simple node by id seek query with multiple values") {
    val astCollection = Collection(
      Seq(SignedIntegerLiteral("42")(pos), SignedIntegerLiteral("43")(pos), SignedIntegerLiteral("43")(pos))
    )(pos)
    val logicalPlan = NodeByIdSeek(IdName("n"), Seq(astCollection))(Seq.empty)
    val pipeInfo = planBuilder.build(logicalPlan)

    pipeInfo should not be 'updating
    pipeInfo.periodicCommit should equal(None)
    pipeInfo.pipe should equal(NodeByIdSeekPipe("n", Seq(astCollection.asCommandExpression)))
  }

  test("simple relationship by id seek query") {
    val astLiteral = SignedIntegerLiteral("42")(pos)
    val fromNode = "from"
    val toNode = "to"
    val logicalPlan = DirectedRelationshipByIdSeek(IdName("r"), Seq(astLiteral), IdName(fromNode), IdName(toNode))(Seq.empty)
    val pipeInfo = planBuilder.build(logicalPlan)

    pipeInfo should not be 'updating
    pipeInfo.periodicCommit should equal(None)
    pipeInfo.pipe should equal(DirectedRelationshipByIdSeekPipe("r", Seq(astLiteral.asCommandExpression), toNode, fromNode))
  }

  test("simple relationship by id seek query with multiple values") {
    val astCollection =
      Seq(SignedIntegerLiteral("42")(pos), SignedIntegerLiteral("43")(pos), SignedIntegerLiteral("43")(pos))

    val fromNode = "from"
    val toNode = "to"
    val logicalPlan = DirectedRelationshipByIdSeek(IdName("r"), astCollection, IdName(fromNode), IdName(toNode))(Seq.empty)
    val pipeInfo = planBuilder.build(logicalPlan)

    pipeInfo should not be 'updating
    pipeInfo.periodicCommit should equal(None)
    pipeInfo.pipe should equal(DirectedRelationshipByIdSeekPipe("r", astCollection.map(_.asCommandExpression), toNode, fromNode))
  }

  test("simple undirected relationship by id seek query with multiple values") {
    val astCollection =
      Seq(SignedIntegerLiteral("42")(pos), SignedIntegerLiteral("43")(pos), SignedIntegerLiteral("43")(pos))

    val fromNode = "from"
    val toNode = "to"
    val logicalPlan = UndirectedRelationshipByIdSeek(IdName("r"), astCollection, IdName(fromNode), IdName(toNode))(Seq.empty)
    val pipeInfo = planBuilder.build(logicalPlan)

    pipeInfo should not be 'updating
    pipeInfo.periodicCommit should equal(None)
    pipeInfo.pipe should equal(UndirectedRelationshipByIdSeekPipe("r", astCollection.map(_.asCommandExpression), toNode, fromNode))
  }

  test("simple cartesian product") {
    val lhs = AllNodesScan(IdName("n"))
    val rhs = AllNodesScan(IdName("m"))
    val logicalPlan = CartesianProduct(lhs, rhs)
    val pipeInfo = planBuilder.build(logicalPlan)

    pipeInfo.pipe should equal(CartesianProductPipe(AllNodesScanPipe("n"), AllNodesScanPipe("m")))
  }

  test("simple expand") {
    val logicalPlan = Expand( AllNodesScan("a"), "a", Direction.INCOMING, Seq(), "b", "r1" )
    val pipeInfo = planBuilder.build(logicalPlan)

    pipeInfo.pipe should equal(ExpandPipe( AllNodesScanPipe("a"), "a", "r1", "b", Direction.INCOMING, Seq() ))
  }

  test("simple hash join") {
    val logicalPlan =
      NodeHashJoin(
        "b",
        Expand( AllNodesScan("a"), "a", Direction.INCOMING, Seq(), "b", "r1" ),
        Expand( AllNodesScan("c"), "c", Direction.INCOMING, Seq(), "b", "r2" )
      )
    val pipeInfo = planBuilder.build(logicalPlan)

    pipeInfo.pipe should equal(NodeHashJoinPipe(
      "b",
      ExpandPipe( AllNodesScanPipe("a"), "a", "r1", "b", Direction.INCOMING, Seq() ),
      ExpandPipe( AllNodesScanPipe("c"), "c", "r2", "b", Direction.INCOMING, Seq() )
    ))
  }
}
