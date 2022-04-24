import akka.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
import org.scalatest.wordspec.AnyWordSpecLike
import io.scalac.auction._

class LotActorSpec extends ScalaTestWithActorTestKit with AnyWordSpecLike {
  import io.scalac.auction.LotActor._

  "Functional Lot Actor " must {

    "reply with 0 amount and empty user id if no bid was sent" in {
      val probe = createTestProbe[Bid]()
      val lotActor = spawn(LotActor("1"))

      lotActor ! GetCurrentlyWinningBid(probe.ref)
      val response = probe.receiveMessage()

      response.offer should ===(0)
      response.userId should ===("")
    }

    "reply with latest accepted bid" in {
      val probe = createTestProbe[Bid]()
      val lotActor = spawn(LotActor("1"))

      lotActor ! Bid(100, "21")
      lotActor ! GetCurrentlyWinningBid(probe.ref)
      val response = probe.receiveMessage()

      response.offer should ===(100)
      response.userId should ===("21")
    }
  }
}


