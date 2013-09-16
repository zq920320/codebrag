package com.softwaremill.codebrag.rest

import com.typesafe.scalalogging.slf4j.Logging
import akka.actor.{ActorSystem, ActorRef}
import com.softwaremill.codebrag.service.updater.LocalRepositoryUpdater
import org.scalatra.{AsyncResult, FutureSupport}
import scala.concurrent.ExecutionContext
import akka.util.Timeout

class RepositorySyncServlet(system: ActorSystem, repositoryUpdateActor: ActorRef) extends JsonServlet with Logging with FutureSupport {

  protected implicit def executor: ExecutionContext = system.dispatcher

  import akka.pattern.ask

  implicit val timeout2 = Timeout(10000)

  get("/") {
    new AsyncResult() {
      val is = repositoryUpdateActor ? LocalRepositoryUpdater.UpdateCommand
    }
  }
}

object RepositorySyncServlet {
  val Mapping = "/sync"
}
