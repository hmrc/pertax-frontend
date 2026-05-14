package models

import play.twirl.api.Html

enum Type:
  case BasicCard
  case BasicCardWithDueDate
  case SectionCard
  case NoLinkCard
  case NewTabCard

class Heading(val text: String, val url: Option[String])
class Body(val content: Html)
class Hint(val content: Option[String], val tag: Option[Html])

case class HmrcCardModel(
  card_type: Type,
  heading: Heading,
  body: Option[Body],
  hint: Option[Hint],
)