package io.scalac.auction

import akka.actor.Cancellable
import akka.actor.typed.scaladsl.Behaviors


// this is not persistent actor!
object TransactionsListenerActor {
  type Transaction = Cancellable

  trait Command

  case class AddCurrentTransaction(transactionId: String, transaction: Transaction) extends Command
  case class DeleteCurrentTransaction(transactionId: String) extends Command

  def apply: Behaviors.Receive[Command] = apply(Map())

  def apply(transactions: Map[String, Transaction]): Behaviors.Receive[Command] = Behaviors.receive {

    case (ctx, AddCurrentTransaction(transactionId, transaction)) =>
      ctx.log.info(s"Listening to transaction: $transactionId")
      apply(transactions + (transactionId -> transaction))

    case (ctx, DeleteCurrentTransaction(transactionId)) =>
      ctx.log.info(s"Stop listening to transaction: $transactionId")
      transactions(transactionId).cancel()

      apply(transactions - transactionId)
  }
}
