package io.shiftleft.dataflowengineoss

import io.shiftleft.codepropertygraph.generated.nodes
import io.shiftleft.dataflowengineoss.language.nodemethods.TrackingPointMethods
import io.shiftleft.semanticcpg.language.Steps
import io.shiftleft.semanticcpg.language._
import io.shiftleft.semanticcpg.language.nodemethods.AstNodeMethods
import io.shiftleft.semanticcpg.language.types.expressions.generalizations.AstNode

package object language {

  implicit def trackingPointBaseMethodsQp[NodeType <: nodes.TrackingPoint](node: NodeType): TrackingPointMethods =
    new TrackingPointMethods(node.asInstanceOf[nodes.TrackingPoint])

  implicit def toTrackingPoint[NodeType <: nodes.TrackingPointBase](steps: Steps[NodeType]): TrackingPoint =
    new TrackingPoint(new NodeSteps(steps.raw.cast[nodes.TrackingPoint]))

  implicit def trackingPointToAstNodeMethods(node: nodes.TrackingPoint) =
    new AstNodeMethods(trackingPointToAstNode(node))

  private def trackingPointToAstNode(node: nodes.TrackingPoint): nodes.AstNode = node match {
    case n: nodes.AstNode               => n
    case n: nodes.DetachedTrackingPoint => n.cfgNode
    case _                              => ??? //TODO markus/fabs?
  }

  implicit def trackingPointToAstBase(steps: NodeSteps[nodes.TrackingPoint]): AstNode[nodes.AstNode] =
    new AstNode(steps.map(trackingPointToAstNode))

}
