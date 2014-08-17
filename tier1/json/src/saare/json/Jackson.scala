/*
Copyright 2013 sumito3478 <sumito3478@gmail.com>

This file is part of the Saare Library.

This software is free software; you can redistribute it and/or modify it
under the terms of the GNU Lesser General Public License as published by the
Free Software Foundation; either version 3 of the License, or (at your
option) any later version.

This software is distributed in the hope that it will be useful, but WITHOUT
ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public License
for more details.

You should have received a copy of the GNU Lesser General Public License
along with this software. If not, see http://www.gnu.org/licenses/.
*/
package saare
package json

private[json] object Jackson {
  import saare._, Saare._
  import json._, Json._
  import com.fasterxml.jackson
  import jackson.core._
  import jackson.databind._
  import jackson.databind.jsontype._
  import jackson.databind.ser.std._
  import jackson.databind.deser.std._
  import jackson.databind.`type`._
  import jackson.databind.module._
  import scala.util.control.Exception._

  private[this] object JValueSerializer extends StdSerializer[JValue](classOf[JValue]) {
    override def serialize(value: JValue, gen: JsonGenerator, provider: SerializerProvider): Unit = {
      value match {
        case JBoolean(x) => gen.writeBoolean(x)
        case JNumber(x) => gen.writeNumber(x.bigDecimal)
        case JString(x) => gen.writeString(x)
        case JArray(xs) =>
          gen.writeStartArray
          for (x <- xs) serialize(x, gen, provider)
          gen.writeEndArray
        case JObject(xs) =>
          gen.writeStartObject
          for ((k, v) <- xs) {
            gen.writeFieldName(k)
            serialize(v, gen, provider)
          }
          gen.writeEndObject
        case JNull => gen.writeNull
        case JNothing =>
      }
    }
    // is this necessary?
    override def serializeWithType(value: JValue, gen: JsonGenerator, provider: SerializerProvider, typeSer: TypeSerializer): Unit = {
      typeSer.writeTypePrefixForScalar(value, gen)
      serialize(value, gen, provider)
      typeSer.writeTypeSuffixForScalar(value, gen)
    }
  }
  private[this] object JValueDeserializer extends StdDeserializer[JValue](classOf[JValue]) {
    override def deserialize(p: JsonParser, ctx: DeserializationContext): JValue = {
      import JsonToken._
      p.getCurrentToken match {
        case START_OBJECT =>
          def loop(xs: List[(String, JValue)]): JObject =
            p.nextToken match {
              case END_OBJECT => JObject(xs.reverse.toMap)
              case _ =>
                val name = p.getCurrentName
                p.nextToken
                val value = deserialize(p, ctx)
                loop((name, value) :: xs)
            }
          loop(List())
        case START_ARRAY =>
          def loop(xs: List[JValue]): JArray =
            p.nextToken match {
              case END_ARRAY => JArray(xs.reverse)
              case _ => loop(deserialize(p, ctx) :: xs)
            }
          loop(List())
        case VALUE_EMBEDDED_OBJECT => throw ctx.mappingException(classOf[JValue])
        case VALUE_FALSE => JBoolean(false)
        case VALUE_TRUE => JBoolean(true)
        case VALUE_NULL => JNull
        case VALUE_NUMBER_FLOAT => JNumber(BigDecimal(p.getDecimalValue))
        case VALUE_NUMBER_INT => JNumber(BigDecimal(p.getBigIntegerValue))
        case VALUE_STRING => JString(p.getText)
        case _ => throw ctx.mappingException(classOf[JValue])
      }
    }
    override def deserializeWithType(p: JsonParser, ctx: DeserializationContext, typeDeser: TypeDeserializer) = typeDeser.deserializeTypedFromScalar(p, ctx)
  }
  private[this] object Module extends SimpleModule(Version.unknownVersion) {
    addSerializer(JValueSerializer)
    setDeserializers(new SimpleDeserializers {
      override def findBeanDeserializer(ty: JavaType, config: DeserializationConfig, beanDesc: BeanDescription): JsonDeserializer[_] =
        if (classOf[JValue].isAssignableFrom(ty.getRawClass)) JValueDeserializer else null
      override def findCollectionDeserializer(ty: CollectionType, config: DeserializationConfig, beanDesc: BeanDescription, elementTypeDeserializer: TypeDeserializer, elementDeserializer: JsonDeserializer[_]): JsonDeserializer[_] =
        if (classOf[JArray].isAssignableFrom(ty.getRawClass)) JValueDeserializer else null
      override def findMapDeserializer(ty: MapType, config: DeserializationConfig, beanDesc: BeanDescription, keyDeserializer: KeyDeserializer, elementTypeDeserializer: TypeDeserializer,
        elementDeserializer: JsonDeserializer[_]): JsonDeserializer[_] =
        if (classOf[JObject].isAssignableFrom(ty.getRawClass)) JValueDeserializer else null
    })
  }
  private[this] val mapper = new ObjectMapper
  mapper.registerModule(Module)

  private[this] val prettyPrintingMapper = mapper.copy.enable(SerializationFeature.INDENT_OUTPUT)

  def readJValue(json: String) = allCatch[JValue].withTry(mapper.readValue[JValue](json, classOf[JValue]))

  def writeJValue(json: JValue) = mapper.writeValueAsString(json)

  def prettyPrintJValue(json: JValue) = prettyPrintingMapper.writeValueAsString(json)
}