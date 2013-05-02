package com.softwaremill.codebrag.rest

import com.softwaremill.codebrag.service.user.Authenticator
import org.scalatra.swagger.{SwaggerSupport, Swagger}
import org.scalatra.json.JacksonJsonSupport
import com.softwaremill.codebrag.dao.reporting._
import scala.Some
import com.softwaremill.codebrag.dao.FollowupDAO
import org.bson.types.ObjectId
import com.softwaremill.codebrag.dao.reporting.views.FollowupListView

class FollowupsServlet(val authenticator: Authenticator,
                       val swagger: Swagger,
                       followupFinder: FollowupFinder,
                       followupDao: FollowupDAO)
  extends JsonServletWithAuthentication with FollowupsServletSwaggerDefinition with JacksonJsonSupport {

  get("/", operation(getOperation)) {
    haltIfNotAuthenticated
    followupFinder.findAllFollowupsForUser(new ObjectId(user.id))
  }

  delete("/:id", operation(dismissOperation)) {
    haltIfNotAuthenticated
    val commitId = params("id")
    followupDao.delete(new ObjectId(commitId), new ObjectId(user.id))
  }
}

trait FollowupsServletSwaggerDefinition extends SwaggerSupport {

  override protected val applicationName = Some(FollowupsServlet.MappingPath)
  protected val applicationDescription: String = "Follow-ups endpoint"

  val getOperation = apiOperation[FollowupListView]("get")
  val dismissOperation = apiOperation[Unit]("dismiss")
    .summary("Deletes selected follow-up for current user")
    .parameter(pathParam[String]("id").description("Commit identifier").required)
}

object FollowupsServlet {
  val MappingPath = "followups"
}