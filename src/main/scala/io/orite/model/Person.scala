package io.orite.model

import io.circe.generic.JsonCodec

@JsonCodec
case class Person(name:String, age:Int)
