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
import org.neo4j.graphdb.Direction
import org.neo4j.cypher.SyntaxException

trait Predicates extends JavaTokenParsers with Tokens with Expressions {
  def predicate: Parser[Predicate] = (isNull | isNotNull | orderedComparison | not | notEquals | equals | regexp | hasProperty | parens(predicate) | sequencePredicate | hasRelationshipTo | hasRelationship) * (
    ignoreCase("and") ^^^ {
      (a: Predicate, b: Predicate) => And(a, b)
    } |
      ignoreCase("or") ^^^ {
        (a: Predicate, b: Predicate) => Or(a, b)
      }
    )

  def regexp: Parser[Predicate] = expression ~ "=~" ~ (regularLiteral | expression) ^^ {
    case a ~ "=~" ~ b => RegularExpression(a, b)
  }

  def hasProperty: Parser[Predicate] = property ^^ {
    case prop => Has(prop.asInstanceOf[Property])
  }

  def sequencePredicate: Parser[Predicate] = (allInSeq | anyInSeq | noneInSeq | singleInSeq)

  def symbolIterablePredicate: Parser[(Expression, String, Predicate)] = identity ~ ignoreCase("in") ~ expression ~ ignoreCase("where") ~ predicate ^^ {
    case symbol ~ in ~ iterable ~ where ~ klas => (iterable, symbol, klas)
  }

  def allInSeq: Parser[Predicate] = ignoreCase("all") ~> parens(symbolIterablePredicate) ^^ (x => AllInIterable(x._1, x._2, x._3))

  def anyInSeq: Parser[Predicate] = ignoreCase("any") ~> parens(symbolIterablePredicate) ^^ (x => AnyInIterable(x._1, x._2, x._3))

  def noneInSeq: Parser[Predicate] = ignoreCase("none") ~> parens(symbolIterablePredicate) ^^ (x => NoneInIterable(x._1, x._2, x._3))

  def singleInSeq: Parser[Predicate] = ignoreCase("single") ~> parens(symbolIterablePredicate) ^^ (x => SingleInIterable(x._1, x._2, x._3))

  def equals: Parser[Predicate] = expression ~ "=" ~ expression ^^ {
    case l ~ "=" ~ r => Equals(l, r)
  }

  def notEquals: Parser[Predicate] = expression ~ ("!=" | "<>") ~ expression ^^ {
    case l ~ wut ~ r => Not(Equals(l, r))
  }

  def orderedComparison: Parser[Predicate] = (lessThanOrEqual | greaterThanOrEqual | lessThan | greaterThan)

  def lessThan: Parser[Predicate] = expression ~ "<" ~ expression ^^ {
    case l ~ "<" ~ r => LessThan(l, r)
  }

  def greaterThan: Parser[Predicate] = expression ~ ">" ~ expression ^^ {
    case l ~ ">" ~ r => GreaterThan(l, r)
  }

  def lessThanOrEqual: Parser[Predicate] = expression ~ "<=" ~ expression ^^ {
    case l ~ "<=" ~ r => LessThanOrEqual(l, r)
  }

  def greaterThanOrEqual: Parser[Predicate] = expression ~ ">=" ~ expression ^^ {
    case l ~ ">=" ~ r => GreaterThanOrEqual(l, r)
  }

  def not: Parser[Predicate] = ignoreCase("not") ~ "(" ~ predicate ~ ")" ^^ {
    case not ~ "(" ~ inner ~ ")" => Not(inner)
  }

  def expressionOrEntity = (expression | entity)

  def isNull: Parser[Predicate] = expressionOrEntity <~ ignoreCase("is null") ^^ (x => IsNull(x))

  def isNotNull: Parser[Predicate] = expressionOrEntity <~ ignoreCase("is not null") ^^ (x => Not(IsNull(x)))

  def hasRelationshipTo: Parser[Predicate] = expressionOrEntity ~ relInfo ~ expressionOrEntity ^^ {
    case a ~ rel ~ b => HasRelationshipTo(a, b, rel._1, rel._2)
  }

  def hasRelationship: Parser[Predicate] = expressionOrEntity ~ relInfo <~ "()" ^^ {
    case a ~ rel  => HasRelationship(a, rel._1, rel._2)
  }

  def relInfo: Parser[(Direction, Option[String])] = opt("<") ~ "-" ~ opt("[:" ~> identity <~ "]") ~ "-" ~ opt(">") ^^ {
    case Some("<") ~ "-" ~ relType ~ "-" ~ Some(">") => throw new SyntaxException("Can't be connected both ways.")
    case Some("<") ~ "-" ~ relType ~ "-" ~ None => (Direction.INCOMING, relType)
    case None ~ "-" ~ relType ~ "-" ~ Some(">") => (Direction.OUTGOING, relType)
    case None ~ "-" ~ relType ~ "-" ~ None => (Direction.BOTH, relType)
  }
}













