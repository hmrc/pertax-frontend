/*
 * Copyright 2023 HM Revenue & Customs
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package config

import com.google.inject.{Inject, Singleton}
import controllers.auth.requests.UserRequest
import models.NewsAndContentModel
import play.api.Configuration
import play.api.i18n.Messages
import play.api.mvc.AnyContent
import util.LocalDateUtilities

import java.time.LocalDate
import java.time.format.DateTimeFormatter

@Singleton
class NewsAndTilesConfig @Inject() (configuration: Configuration, localDateUtilities: LocalDateUtilities) {

  implicit val localDateOrdering: Ordering[LocalDate] = Ordering.by(-_.toEpochDay)

  def getNewsAndContentModelList()(implicit
    messages: Messages,
    request: UserRequest[AnyContent]
  ): List[NewsAndContentModel] = {
    val config = configuration.underlying

    val maxNewsItems: Int = configuration.getOptional[Int]("feature.news.max").getOrElse(10)
    val totalNewsItems    = (0 until maxNewsItems).takeWhile(i => config.hasPathOrNull(s"feature.news.items.$i.name")).size
    (0 until totalNewsItems)
      .map { i =>
        val newsSection                                          = configuration.get[String](s"feature.news.items.$i.name")
        val enrolmentsNeeded                                     = configuration.getOptional[String](s"feature.news.items.$i.enrolment")
        val formatter                                            = DateTimeFormatter.ofPattern("yyyy-MM-dd")
        val localStartDate                                       =
          LocalDate.parse(configuration.get[String](s"feature.news.items.$i.start-date"), formatter)
        val optionalEndDate                                      = configuration.getOptional[String](s"feature.news.items.$i.end-date")
        val localEndDate                                         = optionalEndDate match {
          case Some(endDate) => LocalDate.parse(endDate, formatter)
          case None          => LocalDate.MAX
        }
        val overrideStartAndEndDatesForNewsItemsEnabled: Boolean = configuration
          .getOptional[String]("feature.news.override-start-and-end-dates.enabled")
          .getOrElse("false")
          .toBoolean
        val displayedByEnrolment                                 =
          request.enrolments.exists { enrolment =>
            enrolmentsNeeded.contains(enrolment.key) || enrolmentsNeeded.isEmpty
          }
        if (
          (overrideStartAndEndDatesForNewsItemsEnabled || localDateUtilities.isBetween(
            LocalDate.now(),
            localStartDate,
            localEndDate
          )) && displayedByEnrolment
        ) {
          configuration.getOptional[Boolean](s"feature.news.items.$i.dynamic-content") match {
            case Some(_) => Some(NewsAndContentModel(newsSection, "", "", isDynamic = true, localStartDate))
            case None    =>
              val shortDescription = if (messages.lang.code equals "en") {
                configuration.get[String](s"feature.news.items.$i.short-description-en")
              } else {
                configuration.get[String](s"feature.news.items.$i.short-description-cy")
              }
              val content          = if (messages.lang.code equals "en") {
                configuration.get[String](s"feature.news.items.$i.content-en")
              } else {
                configuration.get[String](s"feature.news.items.$i.content-cy")
              }
              Some(NewsAndContentModel(newsSection, shortDescription, content, isDynamic = false, localStartDate))
          }
        } else {
          None
        }
      }
      .toList
      .flatten
      .sortBy(_.startDate)
  }
}
