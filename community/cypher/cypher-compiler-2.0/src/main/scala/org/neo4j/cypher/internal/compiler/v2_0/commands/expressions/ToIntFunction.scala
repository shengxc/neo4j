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
package org.neo4j.cypher.internal.compiler.v2_0.commands.expressions

import org.neo4j.cypher.internal.compiler.v2_0.{symbols, ExecutionContext}
import org.neo4j.cypher.internal.compiler.v2_0.pipes.QueryState
import symbols._
import org.neo4j.cypher.ParameterWrongTypeException

case class ToIntFunction(a: Expression) extends NullInNullOutExpression(a) {
  def symbolTableDependencies: Set[String] = a.symbolTableDependencies

  /*When calculating the type of an expression, the expression should also
    make sure to check the types of any downstream expressions*/
  protected def calculateType(symbols: SymbolTable): CypherType = CTInteger

  // Expressions that do not get anything in their context from this expression.
  def arguments: Seq[Expression] = Seq(a)

  def rewrite(f: (Expression) => Expression): Expression = f(ToIntFunction(a.rewrite(f)))

  def compute(value: Any, m: ExecutionContext)(implicit state: QueryState): Any = {
    a(m) match {
      case v: String => Integer.parseInt(v)
      case v: Number => v.intValue()
      case v: Boolean => if (v) 1 else 0
      case v => throw new ParameterWrongTypeException("Expected a string, number or boolean, got: " + v.toString)
    }
  }
}