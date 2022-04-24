package io.scalac.auction

import akka.http.scaladsl.Http
import io.scalac.auction.AuctionServiceApp._

import scala.io.StdIn

object AuctionServiceServer {

  def main(args: Array[String]): Unit = {

    AuctionServiceApp.initializeUserService()
    AuctionServiceApp.initializeTransactionsListener()

    val bindingFuture = Http().newServerAt("localhost", 8080).bind(Routes.getRoute())
    println(s"Server online at http://localhost:8080/\nPress RETURN to stop...")
    StdIn.readLine()
    bindingFuture
      .flatMap(_.unbind())
      .onComplete(_ => system.terminate())

  }
}
