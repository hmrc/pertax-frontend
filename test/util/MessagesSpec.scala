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

package util

import play.api.i18n.MessagesApi
import testUtils.BaseSpec

import scala.util.matching.Regex

class MessagesSpec extends BaseSpec {

  lazy val messagesAPI = app.injector.instanceOf[MessagesApi]

  val matchSingleQuoteOnly: Regex   = """\w+'{1}\w+""".r
  val matchBacktickQuoteOnly: Regex = """`+""".r

  "Application" must {
    "have the correct message configs" in {
      messagesAPI.messages.keys must contain theSameElementsAs Vector("en", "cy", "default", "default.play")
    }

    "have messages for default and cy only" in {
      val englishMessageCount = messagesAPI.messages("en").size

      englishMessageCount mustBe 17

      val defaultMessageCount = messagesAPI.messages("default").size

      messagesAPI.messages("cy").size mustBe defaultMessageCount + englishMessageCount
    }
  }

  "All message files" must {
    "have the same set of keys" in {
      withClue(mismatchingKeys(defaultMessages.keySet ++ englishMessages.keySet, welshMessages.keySet)) {
        assert(welshMessages.keySet equals defaultMessages.keySet ++ englishMessages.keySet)
      }
    }

    "not have the same messages" in {
      val same = defaultMessages.keys.collect({
        case key if defaultMessages.get(key) == welshMessages.get(key) =>
          (key, defaultMessages.get(key))
      })

      val percentageOfSameMessages = 0.04

      // 96% of app needs to be translated into Welsh. 96% allows for:
      //   - Messages which just can't be different from English
      //     E.g. addresses, acronyms, numbers, etc.
      //   - Content which is pending translation to Welsh
      f"${same.size.toDouble / defaultMessages.size.toDouble}%.2f".toDouble <= percentageOfSameMessages mustBe true
    }

    "have a non-empty message for each key" in {
      assertNonEmptyNonTemporaryValues("Default", defaultMessages)
      assertNonEmptyNonTemporaryValues("Welsh", welshMessages)
    }
    "have no unescaped single quotes in value" in {
      assertCorrectUseOfQuotes("Default", defaultMessages)
      assertCorrectUseOfQuotes("Welsh", welshMessages)
    }
    "have a resolvable message for keys which take args" in {
      val englishWithArgsMsgKeys = defaultMessages collect { case (key, value) if countArgs(value) > 0 => key }
      val welshWithArgsMsgKeys   = welshMessages collect { case (key, value) if countArgs(value) > 0 => key }
      val missingFromEnglish     = englishWithArgsMsgKeys.toList diff welshWithArgsMsgKeys.toList
      val missingFromWelsh       = welshWithArgsMsgKeys.toList diff englishWithArgsMsgKeys.toList
      missingFromEnglish foreach { key =>
        println(s"Key which has arguments in English but not in Welsh: $key")
      }
      missingFromWelsh foreach { key =>
        println(s"Key which has arguments in Welsh but not in English: $key")
      }
      englishWithArgsMsgKeys.size mustBe welshWithArgsMsgKeys.size
    }
    "have the same args in the same order for all keys which take args" in {
      val englishWithArgsMsgKeysAndArgList = defaultMessages collect {
        case (key, value) if countArgs(value) > 0 => (key, listArgs(value))
      }
      val welshWithArgsMsgKeysAndArgList   = welshMessages collect {
        case (key, value) if countArgs(value) > 0 => (key, listArgs(value))
      }
      val mismatchedArgSequences           = englishWithArgsMsgKeysAndArgList collect {
        case (key, engArgSeq) if engArgSeq != welshWithArgsMsgKeysAndArgList(key) =>
          (key, engArgSeq, welshWithArgsMsgKeysAndArgList(key))
      }
      mismatchedArgSequences foreach { case (key, engArgSeq, welshArgSeq) =>
        println(
          s"key which has different arguments or order of arguments between English and Welsh: $key -- English arg seq=$engArgSeq and Welsh arg seq=$welshArgSeq"
        )
      }
      mismatchedArgSequences.size mustBe 0
    }
  }

  private def toArgArray(msg: String) =
    msg
      .split("\\{|\\}")
      .map(_.trim())
      .filter(_.forall(_.isDigit))

  private def countArgs(msg: String) = toArgArray(msg).length

  private def listArgs(msg: String) = toArgArray(msg).mkString

  private def assertNonEmptyNonTemporaryValues(label: String, messages: Map[String, String]) = messages.foreach {
    case (key: String, value: String) =>
      withClue(s"In $label, there is an empty value for the key:[$key][$value]") {
        value.trim.isEmpty mustBe false
      }
  }

  private def assertCorrectUseOfQuotes(label: String, messages: Map[String, String]) = messages.foreach {
    case (key: String, value: String) =>
      withClue(s"In $label, there is an unescaped or invalid quote:[$key][$value]") {
        matchSingleQuoteOnly.findFirstIn(value).isDefined mustBe false
        matchBacktickQuoteOnly.findFirstIn(value).isDefined mustBe false
      }
  }

  private def listMissingMessageKeys(header: String, missingKeys: Set[String]) =
    missingKeys.toList.sorted.mkString(header + displayLine, "\n", displayLine)

  private lazy val displayLine = "\n" + ("@" * 42) + "\n"

  private lazy val defaultMessages: Map[String, String] = getExpectedMessages("default") -- commonProvidedKeys

  private lazy val englishMessages: Map[String, String] = getExpectedMessages("en") -- commonProvidedKeys

  private lazy val welshMessages: Map[String, String] = getExpectedMessages("cy") -- commonProvidedKeys

  private def getExpectedMessages(languageCode: String) =
    messagesAPI.messages.getOrElse(languageCode, throw new Exception(s"Missing messages for $languageCode"))

  private def mismatchingKeys(defaultKeySet: Set[String], welshKeySet: Set[String]) = {
    val test1 =
      listMissingMessageKeys("The following message keys are missing from Welsh Set:", defaultKeySet.diff(welshKeySet))
    val test2 = listMissingMessageKeys(
      "The following message keys are missing from English Set:",
      welshKeySet.diff(defaultKeySet)
    )

    test1 ++ test2
  }

  private val commonProvidedKeys = Set(
    "error.address.invalid.character"
  )
}
