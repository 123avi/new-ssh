package com.indeni.ssh.utils

import java.util.concurrent.TimeoutException

import akka.actor.Scheduler
import akka.pattern.after

import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}
import scala.util.Try

/**
  * Some enhanced operations on future
  * inspired by https://gist.github.com/viktorklang/9414163
  *
  */
trait FutureOps {

  /**
    *
    * @param f
    * @param delay
    * @param retries
    * @param ec
    * @param s
    * @tparam T
    * @return
    * @example
    * retry(future, 100 millis, 3)
    */
  def retry[T](f: => Future[T], delay: FiniteDuration, retries: Int)(implicit ec: ExecutionContext, s: Scheduler): Future[T] = {
    f recoverWith { case _ if retries > 0 => after(delay, s)(retry(f, delay, retries - 1 )) }
  }

  /**
    * @note - will execute sequence of delays in order , if retries > delays.length teh default delay will be used
    * @example
    * val delays = List(100 millis, 200 millis, 1 second)
    * retry(future, delays, 5, 500 millis)
    */
  def retry[T](f: => Future[T], delay: Seq[FiniteDuration], retries: Int, defaultDelay: FiniteDuration )(implicit ec: ExecutionContext, s: Scheduler): Future[T] = {
    f recoverWith {
      case _ if retries > 0 => after(delay.headOption.getOrElse(defaultDelay), s)(retry(f, Try {
        delay.tail
      }.getOrElse(List(defaultDelay)), retries - 1, defaultDelay))
    }
  }

  /**
    *
    * @note same as the above but with definition of timeout
    * @example
    * val timeout  = 2 secodns
    * retry(future, 100 millis, 3, timeout)
    */
  def retry[T](f: => Future[T], delay: FiniteDuration, retries: Int, timeout: FiniteDuration )(implicit ec: ExecutionContext, s: Scheduler): Future[T] = {
    val timeoutFuture: Future[Nothing] = after(timeout, s) { Future.failed(new TimeoutException("Future timed out!")) }
    Future.firstCompletedOf(Seq(f,timeoutFuture)) recoverWith {
      case e:TimeoutException =>
        Future.failed(e)
      case _ if retries > 0 =>
      after(delay, s)(retry(f, delay, retries - 1 , timeout))
    }
  }

  /**
    * choose your flavor, in case you like to use it with implicits
    *
    * @param f
    * @tparam T
    * @example future.retry(...)
    */
  implicit class FutureImplicits[T](f: => Future[T]) extends FutureOps {

    def retry(delay: FiniteDuration, retries: Int)(implicit ec: ExecutionContext, s: Scheduler): Future[T] = super.retry(f, delay, retries)

    def retry(delay: FiniteDuration, retries: Int, timeout: FiniteDuration)(implicit ec: ExecutionContext, s: Scheduler): Future[T] = super.retry(f, delay, retries, timeout)

    def retry(delay: Seq[FiniteDuration], retries: Int, defaultDelay: FiniteDuration)(implicit
    ec: ExecutionContext, s: Scheduler): Future[T] = super.retry(f, delay, retries, defaultDelay)


  }

}
