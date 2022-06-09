package models

trait SingleAccountEnrolment

case object SingleAccountNotEnrolled extends SingleAccountEnrolment
case class SingleAccountEnrolled(status: EnrolmentStatus) extends SingleAccountEnrolment

