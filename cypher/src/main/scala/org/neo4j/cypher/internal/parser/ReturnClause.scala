/**
 * Copyright (c) 2002-2011 "Neo Technology,"
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
package org.neo4j.cypher.internal.parser

import org.neo4j.cypher.commands._
import scala.util.parsing.combinator._

trait ReturnClause extends JavaTokenParsers with Tokens with ReturnItems {

  def column = (aggregate | returnItem) ~ opt(ignoreCase("AS") ~> identity) ^^ {
    case returnItem ~ alias => {
      alias match {
        case None => returnItem
        case Some(newColumnName) => returnItem match {
          case x: AggregationItem => AliasAggregationItem(x, newColumnName)
          case x => AliasReturnItem(x, newColumnName)
        }
      }
    }
  }

  def returns: Parser[(Return, Option[Aggregation])] = ignoreCase("return") ~> opt("distinct") ~ rep1sep(column, ",") ^^ {
    case distinct ~ items => {
      val aggregationItems = items.filter(_.isInstanceOf[AggregationItem]).map(_.asInstanceOf[AggregationItem])

      val none: Option[Aggregation] = distinct match {
        case Some(x) => Some(Aggregation())
        case None => None
      }

      val aggregation = aggregationItems match {
        case List() => none
        case _ => Some(Aggregation(aggregationItems: _*))
      }

      val returnItems = Return(items.map(_.columnName).toList, items.filter(!_.isInstanceOf[AggregationItem]): _*)

      (returnItems, aggregation)
    }
  }


}





