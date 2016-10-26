package com.indeni.ssh

import akka.actor.ActorSystem
import akka.pattern.ask
import akka.testkit.{ImplicitSender, TestActorRef, TestKit}
import akka.util.Timeout
import com.indeni.ssh.SessionHelper.{Credentials, SshTarget}
import com.indeni.ssh.SessionManager.{SessionCreationFailed, SessionStarted, StartSession, StartTrainingSession, TrainingComplete}
import com.indeni.ssh.common.IndTypes.ShellCommand
import com.indeni.ssh.common._
import org.scalatest.concurrent.{Eventually, IntegrationPatience}
import org.scalatest.time.{Millis, Seconds, Span}
import org.scalatest.{BeforeAndAfterEach, FunSpecLike, Matchers}

import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext}
import com.typesafe.config.ConfigFactory


class SessionManagerSpec extends TestKit(ActorSystem("test-system"))
  with Matchers
  with FunSpecLike
  with StopSystemAfterAll
  with BeforeAndAfterEach
  with ImplicitSender with Eventually with IntegrationPatience {

  implicit override val patienceConfig = PatienceConfig(timeout = scaled(Span(2, Seconds)),
    interval =
      scaled(Span(30, Millis)))
  val promptRx = PromptRegex("\\S+\\s*[$#>]\\s$")
  val prompt = PromptStr(List("""sdf:/sdf/udd/i/ind> """))
  val ENTER_COMMAND: ShellCommand = "\n"

  val discussionMap = Map(
    PromptStr(
      List("more", "Please press your BACKSPACE key: ", "(continue)")) -> Prompt.NEW_LINE
  )
  val authTimeout = 120 seconds
  val testTimeout = 10 seconds

  //using http://sdf.org/
  val address = ConfigFactory.load().getString("test.server.address")
  val port = ConfigFactory.load().getInt("test.server.port")
  val user = ConfigFactory.load().getString("test.server.user")
  val pwd = ConfigFactory.load().getString("test.server.password")
  val target = SshTarget(address, port, Credentials(user, Some(pwd), None, None))
  
  
  implicit val _timeout = Timeout(authTimeout)
  var counter = 0

  def cond(i: Int) = counter == i

  override def beforeEach() = {
    counter = 0
  }

  def client = new SshdClient2 {
    override implicit val ec: ExecutionContext = system.dispatcher
  }

  describe("SessionManager") {

    it("Should negotiate with the server") {

      val sessionManager = TestActorRef(new SessionManager(client.createClient(4, "10000", "10000"), 2))
      val r = Await.result(sessionManager ? StartSession(target, forceAuth = true, prompt), testTimeout)
      assertResult(SessionStarted)(r)
      val cmd = "find /tmp/ -iname '*.sh' \n"
      sessionManager ! ProcessPrompts(discussionMap)
      fishForMessage(authTimeout) {
        case DoneProcessing =>
          lastSender ! CloseChannel
          true
        case _ => false
      }

    }

    it("Should Execute execute command and stop when reading string prompt") {
      val sessionManager = TestActorRef(new SessionManager(client.createClient(4, "10000", "10000"), 2))
      val r = Await.result(sessionManager ? StartSession(target, forceAuth = true, prompt), testTimeout)
      assertResult(SessionStarted)(r)
      val cmd = "find /tmp/ -iname '*.sh' \n"
      sessionManager ! ProcessPrompts(discussionMap)

      fishForMessage(authTimeout) {
        case DoneProcessing if cond(2) =>
          lastSender ! CloseChannel
          true
        case DoneProcessing =>
          counter += 1
          lastSender ! SimpleCommand(cmd)
          false
        case _ => false
      }


    }

    it("Should Execute execute command and stop when reading regex prompt") {
      val sessionManager = TestActorRef(new SessionManager(client.createClient(4, "10000", "10000"), 2))
      val target = SshTarget(address, port, Credentials(user, Some(pwd), None, None))
      val r = Await.result(sessionManager ? StartSession(target, forceAuth = true, promptRx),
        testTimeout)
      assertResult(SessionStarted)(r)
      val cmd = "find /tmp/ -iname '*.sh' \n"
      sessionManager ! ProcessPrompts(discussionMap)
      fishForMessage(authTimeout) {
        case DoneProcessing if cond(2) =>
          lastSender ! CloseChannel
          true
        case DoneProcessing =>
          counter += 1
          lastSender ! SimpleCommand(cmd)
          false
        case _ => false
      }

    }


    it("Should fail to authenticate session with wrong password ") {
      val sessionManager = TestActorRef(new SessionManager(client.createClient(4, "10000", "10000"), 2))
      val target = SshTarget(address, port, Credentials(user, Some("xxxx"), None, None))
      sessionManager ! StartSession(target, forceAuth = true, promptRx)
      expectMsgPF(authTimeout) {
        case SessionCreationFailed(_) => true
        case other =>
          assertResult(SessionCreationFailed)(other)

      }
    }

    it("Should fail to authenticate session with wrong user ") {
      val sessionManager = TestActorRef(new SessionManager(client.createClient(4, "10000", "10000"), 2))
      val target = SshTarget(address, port, Credentials("foo", Some(pwd), None,
        None))
      sessionManager ! StartSession(target, forceAuth = true, prompt)
      expectMsgPF(authTimeout) {
        case SessionCreationFailed(_) => true
        case other =>
          assertResult(SessionCreationFailed)(other)

      }
    }

    it("Should fail to create session with wrong host ") {
      val sessionManager = TestActorRef(new SessionManager(client.createClient(4, "10000", "10000"), 2))
      val fakeAddress = "190.94.73.15"
      val target = SshTarget(fakeAddress, port, Credentials(user, Some(pwd), None,
        None))
      sessionManager ! StartSession(target, forceAuth = true, promptRx)
      expectMsgPF(authTimeout) {
        case SessionCreationFailed(_) => true
        case other =>
          assertResult(SessionCreationFailed)(other)

      }


    }

    it("Should fail to create session with wrong port ") {
      val sessionManager = TestActorRef(new SessionManager(client.createClient(4, "10000", "10000"), 2))
      val target = SshTarget(address, 80, Credentials(user, Some(pwd), None,
        None))
      sessionManager ! StartSession(target, forceAuth = true, promptRx)
      expectMsgPF(authTimeout) {
        case SessionCreationFailed(_) => true
        case other =>
          assertResult(SessionCreationFailed)(other)

      }


    }

    it("Should Execute execute several commands with several childes  ") {
      val numOfExecutors = 2

      val sessionManager = TestActorRef(new SessionManager(client.createClient(4, "10000", "10000"), numOfExecutors))
      val target = SshTarget(address, port, Credentials(user, Some(pwd), None, None))
      val r = Await.result(sessionManager ? StartSession(target, forceAuth = true, prompt), 10 seconds)
      assertResult(SessionStarted)(r)


      val cmd = "locate '*.sh' \n"
      sessionManager ! ProcessPrompts(discussionMap)
      sessionManager ! ProcessPrompts(discussionMap)
      eventually {
        sessionManager.underlyingActor.context.children.size should be(2)
      }
      fishForMessage(90 seconds, "Waiting for DoneReading from Executor after negotiate ") {
        case DoneProcessing =>
          counter += 1
          lastSender ! SimpleCommand(cmd)
          cond(2)
        case o =>
          println(s"GOT ===< $o")
          false
      }
      eventually {
        sessionManager.underlyingActor.context.children.size should be(2)
      }
      sessionManager.underlyingActor.context.children.foreach(c => c ! CloseChannel)

    }

    it("Should Execute execute several commands with single child  ") {
      val sessionManager = TestActorRef(new SessionManager(client.createClient(4, "10000",
        "10000"), 1))
      val target = SshTarget(address, port, Credentials(user, Some(pwd), None, None))
      val r = Await.result(sessionManager ? StartSession(target, forceAuth = true, prompt), 10 seconds)
      assertResult(SessionStarted)(r)


      sessionManager ! ProcessPrompts(discussionMap)
      sessionManager ! ProcessPrompts(discussionMap)
      eventually {
        sessionManager.underlyingActor.context.children.size should be(1)
      }


      fishForMessage(90 seconds, "Waiting for DoneReading from Executor after negotiate ") {
        case DoneProcessing =>
          counter += 1
          lastSender ! CloseChannel
          cond(2)
        case o =>
          println(s"GOT ===< $o")
          false
      }
      eventually {
        sessionManager.underlyingActor.context.children.size should be(0)
      }
    }
  }

  describe("SessionManager - Training") {

    it("Should Execute execute command and stop when reading string prompt test server") {

      val sessionManager = TestActorRef(new SessionManager(client.createClient(1, "10000", "10000"), 2))
      val target = SshTarget(address, port, Credentials(user, Some(pwd), None, None))

      sessionManager ! StartTrainingSession(target, forceAuth = true)
      fishForMessage(authTimeout) {
        case TrainingComplete(prompts) if prompts.nonEmpty => true
        case _ => false
      }

    }

  }
}
