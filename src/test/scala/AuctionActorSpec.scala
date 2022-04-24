import akka.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
import org.scalatest.wordspec.AnyWordSpecLike
import io.scalac.auction._

class AuctionActorSpec extends ScalaTestWithActorTestKit with AnyWordSpecLike {
  import AuctionActor._

  "Functional Auction Actor " must {

    "add exactly 2 lots after InitializeAuction with list containing 2 lots" in {
      val lots: List[Lot] = List(Lot("1"), Lot("2"))
      val functionalAuctionActor = spawn(AuctionActor("auction1"))

      val probe = createTestProbe[Int]()

      functionalAuctionActor ! InitializeAuction(lots)
      functionalAuctionActor ! ChangeStatusTo(InProgress)
      functionalAuctionActor ! Bid("1", 100, "u23")
      functionalAuctionActor ! NumberOfLots(probe.ref)
      val response = probe.receiveMessage()

      response should ===(2)
    }

    "have 3 lots after adding one lot to two initially added lots" in {
      val initialLots: List[Lot] = List(Lot("1"), Lot("2"))
      val functionalAuctionActor = spawn(AuctionActor("auction2"))

      val probe = createTestProbe[Int]()

      functionalAuctionActor ! InitializeAuction(initialLots)
      functionalAuctionActor ! AddLot(Lot("3"))
      functionalAuctionActor ! NumberOfLots(probe.ref)
      val response = probe.receiveMessage()

      response should ===(3)
    }

    "have 0 elements after adding and removing the same lot from empty initialized repo" in {
      val initialLots: List[Lot] = List()
      val functionalAuctionActor = spawn(AuctionActor("auction3"))

      val probe = createTestProbe[Int]()

      functionalAuctionActor ! InitializeAuction(initialLots)
      functionalAuctionActor ! AddLot(Lot("3"))
      functionalAuctionActor ! RemoveLot("3")
      functionalAuctionActor ! NumberOfLots(probe.ref)
      val response = probe.receiveMessage()

      response should ===(0)
    }
  }
}

