package com.softwaremill.codebrag

import com.softwaremill.codebrag.activities.{CommitReviewActivity, AddCommentActivity}
import com.softwaremill.codebrag.common.{RealTimeClock, ObjectIdGenerator, IdGenerator}
import com.softwaremill.codebrag.rest.CodebragSwagger
import com.softwaremill.codebrag.service.comments.{LikeValidator, UserReactionService}
import com.softwaremill.codebrag.service.diff.{DiffWithCommentsService, DiffService}
import com.softwaremill.codebrag.service.followups.{FollowupsGeneratorForReactionsPriorUserRegistration, WelcomeFollowupsGenerator, FollowupService}
import service.commits._
import com.softwaremill.codebrag.service.user._
import com.softwaremill.codebrag.service.events.akka.AkkaEventBus
import com.softwaremill.codebrag.service.actors.ActorSystemSupport
import com.softwaremill.codebrag.usecase.{ChangeUserSettingsUseCase, UnlikeUseCase}
import com.softwaremill.codebrag.service.invitations.{DefaultUniqueHashGenerator, InvitationService}
import com.softwaremill.codebrag.service.email.{EmailService, EmailScheduler}
import com.softwaremill.codebrag.service.notification.NotificationService
import com.softwaremill.codebrag.service.templates.TemplateEngine
import com.softwaremill.codebrag.stats.{InstanceRunStatsSender, StatsHTTPRequestSender, StatsAggregator}
import com.softwaremill.codebrag.dao.Daos
import com.softwaremill.codebrag.repository.Repository

trait Beans extends ActorSystemSupport with CommitsModule with Daos {

  def config: AllConfig
  def repository: Repository

  implicit lazy val clock = RealTimeClock
  implicit lazy val idGenerator: IdGenerator = new ObjectIdGenerator
  lazy val self = this
  lazy val eventBus = new AkkaEventBus(actorSystem)
  lazy val swagger = new CodebragSwagger
  lazy val ghService = new GitHubAuthService(config)
  lazy val followupService = new FollowupService(followupDao, commitInfoDao, commentDao, userDao)
  lazy val likeValidator = new LikeValidator(commitInfoDao, likeDao, userDao)
  lazy val userReactionService = new UserReactionService(commentDao, likeDao, likeValidator, eventBus)
  lazy val emailService = new EmailService(config)
  lazy val emailScheduler = new EmailScheduler(actorSystem, EmailScheduler.createActor(actorSystem, emailService))
  lazy val templateEngine = new TemplateEngine()
  lazy val invitationsService = new InvitationService(invitationDao, userDao, emailService, config, DefaultUniqueHashGenerator, templateEngine)
  lazy val notificationService = new NotificationService(emailScheduler, templateEngine, config, notificationCountFinder, clock)

  lazy val reviewTaskGenerator = new CommitReviewTaskGeneratorActions {
    val userDao = self.userDao
    val commitInfoDao = self.commitInfoDao
    val commitToReviewDao = self.commitReviewTaskDao
    val repoStatusDao = self.repoStatusDao
  }

  lazy val welcomeFollowupsGenerator = new WelcomeFollowupsGenerator(internalUserDao, commentDao, likeDao, followupDao, commitInfoDao, templateEngine)
  lazy val followupGeneratorForPriorReactions = new FollowupsGeneratorForReactionsPriorUserRegistration(commentDao, likeDao, followupDao, commitInfoDao, config)

  lazy val authenticator = new UserPasswordAuthenticator(userDao, eventBus, reviewTaskGenerator)
  lazy val emptyGithubAuthenticator = new GitHubEmptyAuthenticator(userDao)
  lazy val commentActivity = new AddCommentActivity(userReactionService, followupService, eventBus)

  lazy val commitReviewActivity = new CommitReviewActivity(commitReviewTaskDao, commitInfoDao, eventBus)

  lazy val newUserAdder = new NewUserAdder(userDao, eventBus, reviewTaskGenerator, followupGeneratorForPriorReactions, welcomeFollowupsGenerator)
  lazy val registerService = new RegisterService(userDao, newUserAdder, invitationsService, notificationService)

  lazy val diffWithCommentsService = new DiffWithCommentsService(allCommitsFinder, reactionFinder,
    new DiffService(commitInfoDao, diffLoader, repository))

  lazy val statsAggregator = new StatsAggregator(statsFinder, instanceSettingsDao, config)

  lazy val unlikeUseCaseFactory = new UnlikeUseCase(likeValidator, userReactionService)
  lazy val changeUserSettingsUseCase = new ChangeUserSettingsUseCase(userDao)

  lazy val instanceSettings = instanceSettingsDao.readOrCreate match {
    case Left(error) => throw new RuntimeException(s"Cannot properly initialise Instance settings: $error")
    case Right(instance) => instance
  }

  lazy val statsHTTPRequestSender = new StatsHTTPRequestSender(config)
  lazy val instanceRunStatsSender = new InstanceRunStatsSender(statsHTTPRequestSender)
}