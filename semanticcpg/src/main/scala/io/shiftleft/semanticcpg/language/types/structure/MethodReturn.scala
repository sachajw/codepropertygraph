package io.shiftleft.semanticcpg.language.types.structure

import gremlin.scala._
import io.shiftleft.codepropertygraph.generated.{EdgeTypes, nodes}
import overflowdb.traversal.help
import overflowdb.traversal.help.Doc
import io.shiftleft.semanticcpg.language._

@help.Traversal(elementType = classOf[nodes.MethodReturn])
class MethodReturn(val wrapped: NodeSteps[nodes.MethodReturn]) extends AnyVal {
  private def raw: GremlinScala[nodes.MethodReturn] = wrapped.raw

  @Doc("traverse to parent method")
  def method: NodeSteps[nodes.Method] =
    new NodeSteps(raw.in(EdgeTypes.AST).cast[nodes.Method])

  def returnUser: NodeSteps[nodes.Call] =
    new NodeSteps(raw.in(EdgeTypes.AST).in(EdgeTypes.CALL).cast[nodes.Call])

  /**
    *  Traverse to last expressions in CFG.
    *  Can be multiple.
    */
  @Doc("traverse to last expressions in CFG (can be multiple)")
  def cfgLast: NodeSteps[nodes.Expression] =
    new NodeSteps(raw.in(EdgeTypes.CFG).cast[nodes.Expression])

  /**
    * Traverse to return type
    * */
  @Doc("traverse to return type")
  def typ: NodeSteps[nodes.Type] =
    new NodeSteps(raw.out(EdgeTypes.EVAL_TYPE).cast[nodes.Type])

  def toReturn: NodeSteps[nodes.Return] =
    new NodeSteps(raw.map(_.toReturn).collect { case Some(ret) => ret })
}
