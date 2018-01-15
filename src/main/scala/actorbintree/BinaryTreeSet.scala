/**
  * Copyright (C) 2009-2013 Typesafe Inc. <http://www.typesafe.com>
  */
package actorbintree

import actorbintree.BinaryTreeSet._
import akka.actor._

import scala.collection.immutable.Queue

object BinaryTreeSet {

  trait Operation {
    def requester: ActorRef

    def id: Int

    def elem: Int
  }

  trait OperationReply {
    def id: Int
  }

  case class Test(requester: ActorRef, id: Int, elem: Int) extends Operation

  /** Request with identifier `id` to insert an element `elem` into the tree.
    * The actor at reference `requester` should be notified when this operation
    * is completed.
    */
  case class Insert(requester: ActorRef, id: Int, elem: Int) extends Operation

  /** Request with identifier `id` to check whether an element `elem` is present
    * in the tree. The actor at reference `requester` should be notified when
    * this operation is completed.
    */
  case class Contains(requester: ActorRef, id: Int, elem: Int) extends Operation

  /** Request with identifier `id` to remove the element `elem` from the tree.
    * The actor at reference `requester` should be notified when this operation
    * is completed.
    */
  case class Remove(requester: ActorRef, id: Int, elem: Int) extends Operation

  /** Request to perform garbage collection */
  case object GC

  /** Holds the answer to the Contains request with identifier `id`.
    * `result` is true if and only if the element is present in the tree.
    */
  case class ContainsResult(id: Int, result: Boolean) extends OperationReply

  /** Message to signal successful completion of an insert or remove operation. */
  case class OperationFinished(id: Int) extends OperationReply

}

class BinaryTreeSet extends Actor {

  import BinaryTreeSet._

  def createRoot: ActorRef = context.actorOf(BinaryTreeNode.props(0, initiallyRemoved = true))

  var root = createRoot

  // optional
  var pendingQueue = Queue.empty[Operation]

  // optional
  def receive = normal

  // optional
  /** Accepts `Operation` and `GC` messages. */
  val normal: Receive = {

    case operation: Operation => root ! operation

  }

  // optional
  /** Handles messages while garbage collection is performed.
    * `newRoot` is the root of the new binary tree where we want to copy
    * all non-removed elements into.
    */
  def garbageCollecting(newRoot: ActorRef): Receive = ???

}

object BinaryTreeNode {

  trait Position

  case object Left extends Position

  case object Right extends Position

  case class CopyTo(treeNode: ActorRef)

  case object CopyFinished

  def props(elem: Int, initiallyRemoved: Boolean) = Props(classOf[BinaryTreeNode], elem, initiallyRemoved)
}

class BinaryTreeNode(val elem: Int, initiallyRemoved: Boolean) extends Actor {

  import BinaryTreeNode._

  var subtrees = Map[Position, ActorRef]()
  var removed = initiallyRemoved

  // optional
  def receive = normal

  // optional
  /** Handles `Operation` messages and `CopyTo` requests. */
  val normal: Receive = {

    case Insert(sender, id, elemToInsert) => {
      val binaryTreeNode = context.actorOf(Props(new BinaryTreeNode(elemToInsert, false)))
      //element is smaller
      if (elemToInsert < this.elem) {
        if (subtrees.contains(Left)) {
          val left = subtrees(Left)
          left ! Insert(sender, id, elemToInsert)
        }
        else {
          subtrees = subtrees + (Left -> binaryTreeNode)
          sender ! OperationFinished(id)
        }
      }
      //element is larger
      if (elemToInsert > this.elem) {
        //pass on the command to the larger node
        if (subtrees.contains(Right)) {
          val right = subtrees(Right)
          right ! Insert(sender, id, elemToInsert)
        }
        //add the larger node to the subtrees map
        else {
          subtrees = subtrees + (Right -> binaryTreeNode)
          sender ! OperationFinished(id)
        }
      }
      //element already exists
      if (elemToInsert == this.elem) {
        sender ! OperationFinished(id)
      }
    }

    case Contains(sender, id, elemToFind) => {
      if (elemToFind == this.elem && !this.removed) {
        sender ! ContainsResult(id, true)
      }
      else if (elemToFind < this.elem && subtrees.contains(Left)) {
        val left = subtrees(Left)
        left ! Contains(sender, id, elemToFind)
      }
      else if (elemToFind > this.elem && subtrees.contains(Right)) {
        val right = subtrees(Right)
        right ! Contains(sender, id, elemToFind)
      }
      else {
        sender ! ContainsResult(id, false)
      }
    }

    case Remove(sender, id, elemToRemove) => {
      if (elemToRemove == this.elem) {
        this.removed = true
        sender ! OperationFinished(id)
      }
      else if (elemToRemove < this.elem && subtrees.contains(Left)) {
        val left = subtrees(Left)
        left ! Remove(sender, id, elemToRemove)
      }
      else if (elemToRemove > this.elem && subtrees.contains(Right)) {
        val right = subtrees(Right)
        right ! Remove(sender, id, elemToRemove)
      }
    }
  }

  // optional
  /** `expected` is the set of ActorRefs whose replies we are waiting for,
    * `insertConfirmed` tracks whether the copy of this node to the new tree has been confirmed.
    */
  def copying(expected: Set[ActorRef], insertConfirmed: Boolean): Receive = ???

}
