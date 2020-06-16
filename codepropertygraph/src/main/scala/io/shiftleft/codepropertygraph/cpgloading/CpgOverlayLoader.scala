package io.shiftleft.codepropertygraph.cpgloading

import java.io.IOException

import io.shiftleft.codepropertygraph.Cpg
import io.shiftleft.codepropertygraph.generated.nodes.StoredNode
import io.shiftleft.passes.DiffGraph
import io.shiftleft.proto.cpg.Cpg.{CpgOverlay, PropertyValue}
import org.apache.logging.log4j.LogManager
import org.apache.tinkerpop.gremlin.structure.T
import overflowdb._

import scala.collection.mutable
import scala.collection.mutable.ArrayBuffer
import scala.jdk.CollectionConverters._

private[cpgloading] object CpgOverlayLoader {
  private val logger = LogManager.getLogger(getClass)

  /**
    * Load overlays stored in the file with the name `filename`.
    */
  def load(filename: String, baseCpg: Cpg): Unit = {
    val applier = new CpgOverlayApplier(baseCpg.graph)
    ProtoCpgLoader
      .loadOverlays(filename)
      .map { overlays =>
        overlays.foreach(applier.applyDiff)
      }
      .recover {
        case e: IOException =>
          logger.error("Failed to load overlay from " + filename, e)
          Nil
      }
      .get
  }

  def loadInverse(filename: String, baseCpg: Cpg): Unit = {
    ProtoCpgLoader
      .loadDiffGraphs(filename)
      .map { diffGraphs =>
        diffGraphs.toList.reverse.map { diffGraph =>
          DiffGraph.Applier.applyDiff(DiffGraph.fromProto(diffGraph, baseCpg), baseCpg)
        }
      }
      .recover {
        case e: IOException =>
          logger.error("Failed to load overlay from " + filename, e)
          Nil
      }
      .get
  }

}

/**
  * Component to merge CPG overlay into existing graph
  *
  * @param graph the existing (loaded) graph to apply overlay to
  */
private class CpgOverlayApplier(graph: OdbGraph) {
  private val overlayNodeIdToSrcGraphNode: mutable.HashMap[Long, Node] = mutable.HashMap.empty

  /**
    * Applies diff to existing (loaded) OdbGraph
    */
  def applyDiff(overlay: CpgOverlay): Unit = {
    val inverseBuilder: DiffGraph.InverseBuilder = DiffGraph.InverseBuilder.noop
    addNodes(overlay, inverseBuilder)
    addEdges(overlay, inverseBuilder)
    addNodeProperties(overlay, inverseBuilder)
    addEdgeProperties(overlay, inverseBuilder)
  }

  def applyUndoableDiff(overlay: CpgOverlay): DiffGraph = {
    val inverseBuilder: DiffGraph.InverseBuilder = DiffGraph.InverseBuilder.newBuilder
    addNodes(overlay, inverseBuilder)
    addEdges(overlay, inverseBuilder)
    addNodeProperties(overlay, inverseBuilder)
    addEdgeProperties(overlay, inverseBuilder)
    inverseBuilder.build()
  }

  private def addNodes(overlay: CpgOverlay, inverseBuilder: DiffGraph.InverseBuilder): Unit = {
    overlay.getNodeList.asScala.foreach { node =>
      // TODO use odb api: first refactor `ProtoToCpg.addProperties`
      val properties = node.getPropertyList.asScala
      val keyValues = new ArrayBuffer[AnyRef](2 + (2 * properties.size))
      keyValues += T.label
      keyValues += node.getType.name
      properties.foreach { property =>
        ProtoToCpg.addProperties(keyValues, property.getName.name, property.getValue)
      }
      val newNode = graph.graph.addVertex(keyValues.toArray: _*).asInstanceOf[StoredNode]
      inverseBuilder.onNewNode(newNode)
      overlayNodeIdToSrcGraphNode.put(node.getKey, newNode)
    }
  }

  private def addEdges(overlay: CpgOverlay, inverseBuilder: DiffGraph.InverseBuilder) = {
    overlay.getEdgeList.asScala.foreach { edge =>
      val srcOdbNode = getOdbNodeForOverlayId(edge.getSrc)
      val dstOdbNode = getOdbNodeForOverlayId(edge.getDst)

      val properties = edge.getPropertyList.asScala
      // TODO use odb api: first refactor `ProtoToCpg.addProperties`
      val keyValues = new ArrayBuffer[AnyRef](2 * properties.size)
      properties.foreach { property =>
        ProtoToCpg.addProperties(keyValues, property.getName.name, property.getValue)
      }
      val newEdge =
        srcOdbNode.addEdge(edge.getType.toString, dstOdbNode, keyValues.toArray: _*)
      inverseBuilder.onNewEdge(newEdge.asInstanceOf[OdbEdge])
    }
  }

  private def addNodeProperties(overlay: CpgOverlay, inverseBuilder: DiffGraph.InverseBuilder): Unit = {
    overlay.getNodePropertyList.asScala.foreach { additionalNodeProperty =>
      val property = additionalNodeProperty.getProperty
      val odbNode = getOdbNodeForOverlayId(additionalNodeProperty.getNodeId)
      addPropertyToElement(odbNode, property.getName.name, property.getValue, inverseBuilder)
    }
  }

  private def addEdgeProperties(overlay: CpgOverlay, inverseBuilder: DiffGraph.InverseBuilder): Unit = {
    overlay.getEdgePropertyList.asScala.foreach { additionalEdgeProperty =>
      throw new RuntimeException("Not implemented.")
    }
  }

  private def getOdbNodeForOverlayId(id: Long): Node = {
    if (overlayNodeIdToSrcGraphNode.contains(id)) {
      overlayNodeIdToSrcGraphNode(id)
    } else {
      graph
        .nodeOption(id)
        .getOrElse(throw new AssertionError(s"node with id=$id neither found in overlay nodes, nor in existing graph"))
    }
  }

  private def addPropertyToElement(odbElement: OdbElement,
                                   propertyName: String,
                                   propertyValue: PropertyValue,
                                   inverseBuilder: DiffGraph.InverseBuilder): Unit = {
    import PropertyValue.ValueCase._
    odbElement match {
      case storedNode: StoredNode =>
        inverseBuilder.onBeforeNodePropertyChange(storedNode, propertyName)
      case edge: OdbEdge =>
        inverseBuilder.onBeforeEdgePropertyChange(edge, propertyName)
    }

    propertyValue.getValueCase match {
      case INT_VALUE =>
        odbElement.setProperty(propertyName, propertyValue.getIntValue)
      case STRING_VALUE =>
        odbElement.setProperty(propertyName, propertyValue.getStringValue)
      case BOOL_VALUE =>
        odbElement.setProperty(propertyName, propertyValue.getBoolValue)
      case STRING_LIST =>
        val listBuilder = List.newBuilder[String]
        propertyValue.getStringList.getValuesList.forEach(listBuilder.addOne)
        odbElement.setProperty(propertyName, listBuilder.result)
      case VALUE_NOT_SET =>
      case valueCase =>
        throw new RuntimeException("Error: unsupported property case: " + valueCase)
    }
  }

}
