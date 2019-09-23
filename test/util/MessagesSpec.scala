/*
 * Copyright 2019 HM Revenue & Customs
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

package util

import org.junit.runner.RunWith
import org.scalatest.junit.JUnitRunner
import play.api.i18n.{DefaultLangs, DefaultMessagesApi, Lang}
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.{Application, Environment}
import uk.gov.hmrc.play.test.{UnitSpec, WithFakeApplication}

import scala.util.matching.Regex
@RunWith(classOf[JUnitRunner])
class MessagesSpec extends UnitSpec with WithFakeApplication {

  override lazy val fakeApplication: Application = new GuiceApplicationBuilder()
    .configure(
      Map("application.langs" -> "en,cy", "govuk-tax.Test.enableLanguageSwitching" -> true)
    )
    .build()

  val messagesAPI = new DefaultMessagesApi(
    Environment.simple(),
    fakeApplication.configuration,
    new DefaultLangs(fakeApplication.configuration))
  val languageEnglish: Lang = Lang.get("en").getOrElse(throw new Exception())
  val languageWelsh: Lang = Lang.get("cy").getOrElse(throw new Exception())
  val matchSingleQuoteOnly: Regex = """\w+'{1}\w+""".r
  val matchBacktickQuoteOnly: Regex = """`+""".r

  "Application" should {
    "have the correct message configs" in {
      messagesAPI.messages.size shouldBe 4
      messagesAPI.messages.keys should contain theSameElementsAs Vector("en", "cy", "default", "default.play")
    }

    "have messages for default and cy only" in {
      messagesAPI.messages("en").size shouldBe 0
      val englishMessageCount = messagesAPI.messages("default").size - frameworkProvidedKeys.size

      messagesAPI.messages("cy").size shouldBe englishMessageCount
      messagesAPI.messages("default.play").size shouldBe 46
    }
  }

  "All message files" should {
    "have the same set of keys" in {
      withClue(mismatchingKeys(defaultMessages.keySet, welshMessages.keySet)) {
        assert(welshMessages.keySet equals defaultMessages.keySet)
      }
    }
    "not have the same messages" in {
      val same = defaultMessages.keys.collect({
        case key if defaultMessages.get(key) == welshMessages.get(key) =>
          (key, defaultMessages.get(key))
      })

      // 94% of app needs to be translated into Welsh. 94% allows for:
      //   - Messages which just can't be different from English
      //     E.g. addresses, acronyms, numbers, etc.
      //   - Content which is pending translation to Welsh
      same.size.toDouble / defaultMessages.size.toDouble < 0.06 shouldBe true
    }
    "have a non-empty message for each key" in {
      assertNonEmptyValuesForDefaultMessages()
      assertNonEmptyValuesForWelshMessages()
    }
    "have no unescaped single quotes in value" in {
      assertCorrectUseOfQuotesForDefaultMessages()
      assertCorrectUseOfQuotesForWelshMessages()
    }
    "have a resolvable message for keys which take args" in {
      val englishWithArgsMsgKeys = defaultMessages collect { case (key, value) if countArgs(value) > 0 => key }
      val welshWithArgsMsgKeys = welshMessages collect { case (key, value) if countArgs(value) > 0     => key }
      val missingFromEnglish = englishWithArgsMsgKeys.toList diff welshWithArgsMsgKeys.toList
      val missingFromWelsh = welshWithArgsMsgKeys.toList diff englishWithArgsMsgKeys.toList
      missingFromEnglish foreach { key =>
        println(s"Key which has arguments in English but not in Welsh: $key")
      }
      missingFromWelsh foreach { key =>
        println(s"Key which has arguments in Welsh but not in English: $key")
      }
      englishWithArgsMsgKeys.size shouldBe welshWithArgsMsgKeys.size
    }
    "have the same args in the same order for all keys which take args" in {
      val englishWithArgsMsgKeysAndArgList = defaultMessages collect {
        case (key, value) if countArgs(value) > 0 => (key, listArgs(value))
      }
      val welshWithArgsMsgKeysAndArgList = welshMessages collect {
        case (key, value) if countArgs(value) > 0 => (key, listArgs(value))
      }
      val mismatchedArgSequences = englishWithArgsMsgKeysAndArgList collect {
        case (key, engArgSeq) if engArgSeq != welshWithArgsMsgKeysAndArgList(key) =>
          (key, engArgSeq, welshWithArgsMsgKeysAndArgList(key))
      }
      mismatchedArgSequences foreach {
        case (key, engArgSeq, welshArgSeq) =>
          println(
            s"key which has different arguments or order of arguments between English and Welsh: $key -- English arg seq=$engArgSeq and Welsh arg seq=$welshArgSeq")
      }
      mismatchedArgSequences.size shouldBe 0
    }
  }

  private def isInteger(s: String): Boolean = s forall Character.isDigit

  private def toArgArray(msg: String) = msg.split("\\{|\\}").map(_.trim()).filter(isInteger(_))

  private def countArgs(msg: String) = toArgArray(msg).size

  private def listArgs(msg: String) = toArgArray(msg).mkString

  private def assertNonEmptyValuesForDefaultMessages() = assertNonEmptyNonTemporaryValues("Default", defaultMessages)

  private def assertNonEmptyValuesForWelshMessages() = assertNonEmptyNonTemporaryValues("Welsh", welshMessages)

  private def assertCorrectUseOfQuotesForDefaultMessages() = assertCorrectUseOfQuotes("Default", defaultMessages)

  private def assertCorrectUseOfQuotesForWelshMessages() = assertCorrectUseOfQuotes("Welsh", welshMessages)

  private def assertNonEmptyNonTemporaryValues(label: String, messages: Map[String, String]) = messages.foreach {
    case (key: String, value: String) =>
      withClue(s"In $label, there is an empty value for the key:[$key][$value]") {
        value.trim.isEmpty shouldBe false
      }
  }

  private def assertCorrectUseOfQuotes(label: String, messages: Map[String, String]) = messages.foreach {
    case (key: String, value: String) =>
      withClue(s"In $label, there is an unescaped or invalid quote:[$key][$value]") {
        matchSingleQuoteOnly.findFirstIn(value).isDefined shouldBe false
        matchBacktickQuoteOnly.findFirstIn(value).isDefined shouldBe false
      }
  }

  private def listMissingMessageKeys(header: String, missingKeys: Set[String]) =
    missingKeys.toList.sorted.mkString(header + displayLine, "\n", displayLine)

  private lazy val displayLine = "\n" + ("@" * 42) + "\n"

  private lazy val defaultMessages: Map[String, String] = getExpectedMessages("default") -- providedKeys

  private lazy val welshMessages: Map[String, String] = getExpectedMessages("cy") -- commonProvidedKeys

  private def getExpectedMessages(languageCode: String) =
    messagesAPI.messages.getOrElse(languageCode, throw new Exception(s"Missing messages for $languageCode"))

  private def mismatchingKeys(defaultKeySet: Set[String], welshKeySet: Set[String]) = {
    val test1 =
      listMissingMessageKeys("The following message keys are missing from Welsh Set:", defaultKeySet.diff(welshKeySet))
    val test2 = listMissingMessageKeys(
      "The following message keys are missing from English Set:",
      welshKeySet.diff(defaultKeySet))

    test1 ++ test2
  }

  private val commonProvidedKeys = Set(
    "error.address.invalid.character"
  )

  private val frameworkProvidedKeys = Set(
    "global.error.badRequest400.heading",
    "global.error.badRequest400.message",
    "global.error.badRequest400.title",
    "global.error.pageNotFound404.heading",
    "global.error.pageNotFound404.message",
    "global.error.pageNotFound404.title"
  )

  private val providedKeys = commonProvidedKeys ++ frameworkProvidedKeys
}
