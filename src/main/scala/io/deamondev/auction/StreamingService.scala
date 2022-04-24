package io.scalac.auction

import akka.http.scaladsl.model.ws.{Message, TextMessage}
import akka.stream.scaladsl.{Flow, Sink, Source}
import io.scalac.auction.AuctionServiceApp.getCurrentlyWinningBid

import scala.concurrent.duration._

object StreamingService {

  def html(auctionId: String, lotId: String): String =
    s"""
       |<html>
       |  <head>
       |    <script>
       |      var exampleSocket = new WebSocket("ws://localhost:8080/api/stream/web-socket/$auctionId/$lotId");
       |      console.log("starting websocket...");
       |
       |      exampleSocket.onmessage = function(event) {
       |	       var newChild = document.createElement("div");
       |	       newChild.innerText = event.data;
       |	       document.getElementById("1").appendChild(newChild);
       |      };
       |
       |      exampleSocket.onopen = function(event) {
       |	       exampleSocket.send("Socket is open now...");
       |      };
       |
       |      exampleSocket.send("hello, server!");
       |    </script>
       |  </head>
       |
       |  <body>
       |    This is stream for lot <span styles='color: red'>$lotId</span> placed in auction $auctionId, enjoy!
       |    <div id="1">
       |    </div>
       |  </body>
       |
       |</html>
       |""".stripMargin

  def source(auctionId: String, lotId: String) = Source
    .fromIterator(() => Iterator.continually(1))
    .mapAsync(1) { _ =>
      getCurrentlyWinningBid(auctionId, lotId)
    }


  def messages(auctionId: String, lotId: String) = source(auctionId, lotId)
    .throttle(1, 0.5.seconds)
    .map(winningBid => TextMessage(s"Currently winning offer is: ${winningBid.offer} (due to ${winningBid.userId})"))

  def flow(auctionId: String, lotId: String): Flow[Message, Message, Any] = Flow.fromSinkAndSource(
    Sink.foreach[Message](println),
    messages(auctionId, lotId)
  )
}
