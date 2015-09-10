package pl.metastack.metaweb.state.reactive

import pl.metastack.metarx._
import pl.metastack.metaweb.{Provider, state}

import scala.collection.mutable

class Tag(val name: String) extends state.Tag with Node {
  protected val _attributes = Dict[String, Any]()
  private val contents = Buffer[state.Node]()
  private val _events = Dict[String, Any => Unit]()

  val attributeProvider = Provider[String, Any]()
  val eventProvider = Provider[(String, Seq[Any]), Unit]()

  def attributes: Map[String, Any] = _attributes.toMap

  def events: Map[String, Any => Unit] = _events.toMap

  def watchAttributes: ReadChannel[Dict.Delta[String, Any]] =
    _attributes.changes

  def watchEvents: ReadChannel[Dict.Delta[String, Any => Unit]] =
    _events.changes

  def children: Seq[state.Node] = contents.get

  def watchChildren: ReadChannel[Buffer.Delta[state.Node]] = contents.changes

  def clearChildren() {
    contents.clear()
  }

  def append(node: state.Node) {
    contents += node
  }

  def appendAll(nodes: Seq[state.Node]) {
    contents ++= nodes
  }

  def set(node: state.Node) {
    clearChildren()
    append(node)
  }

  def subscribe(node: ReadChannel[state.Node]): ReadChannel[Unit] = {
    clearChildren()
    node.attach(set)
  }

  def setChildren(nodes: Seq[state.Node]) {
    clearChildren()
    appendAll(nodes)
  }

  def subscribeChildren(list: DeltaBuffer[state.Node]): ReadChannel[Unit] = {
    clearChildren()
    contents.changes << list.changes
  }

  def setAttribute[T](attribute: String, value: T) {
    _attributes.insertOrUpdate(attribute, value)
  }

  def getAttribute(attribute: String): Option[Any] = {
    _attributes.get(attribute).orElse(
      attributeProvider.poll(attribute)
    )
  }

  def updateAttribute[T](attribute: String, value: T) {
    _attributes.update(attribute, value)
  }

  def subscribeAttribute[T](attribute: String, from: ReadChannel[T]): ReadChannel[Unit] =
    from.attach(value =>
      _attributes.insertOrUpdate(attribute, value)
    )

  def setEvent[T](event: String, f: Any => Unit) {
    _events.insertOrUpdate(event, f)
  }

  def triggerAction(action: String, arguments: Any*) {
    eventProvider.poll((action, arguments))
    if (_events.isDefinedAt$(action)) _events(action)(arguments)
  }

  val twoWay = mutable.Set.empty[String]

  def bindAttribute[T](attribute: String, ch: Channel[T]): ReadChannel[Unit] = {
    twoWay += attribute
    val ignore = ch.attach { value =>
      _attributes.insertOrUpdate(attribute, value)
    }

    ch << (_attributes.value(attribute).values.tail.asInstanceOf[ReadChannel[T]], ignore)
  }

  def bindAttributeOpt[T](attribute: String, ch: Channel[Option[T]]): ReadChannel[Unit] = {
    twoWay += attribute
    // TODO Provide Dict.bind(key, ch)

    val ignore = ch.attach {
      case None => _attributes.removeIfExists(attribute)
      case Some(v) => _attributes.insertOrUpdate(attribute, v)
    }

    ch << (_attributes.value(attribute).tail.asInstanceOf[ReadChannel[Option[T]]], ignore)
  }
}