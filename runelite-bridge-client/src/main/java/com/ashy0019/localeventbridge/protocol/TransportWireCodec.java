package com.ashy0019.localeventbridge.protocol;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import java.math.BigDecimal;
import java.util.EnumSet;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

/** Explicit JSON codec for local event transport control, event, and state frames. */
public final class TransportWireCodec
{
	private final Gson gson;
	private final LocalEventWireCodec eventCodec;

	public TransportWireCodec(Gson gson)
	{
		this.gson = Objects.requireNonNull(gson, "gson");
		this.eventCodec = new LocalEventWireCodec(gson);
	}

	public String encode(TransportMessage message)
	{
		Objects.requireNonNull(message, "message");
		JsonObject root = new JsonObject();
		root.addProperty("protocol", TransportProtocol.NAME);
		root.addProperty("version", TransportProtocol.VERSION);
		root.addProperty("kind", kindToWire(message.getKind()));
		root.add("payload", payload(message));
		String json = gson.toJson(root);
		if (json.length() > TransportProtocol.MAX_MESSAGE_CHARS)
		{
			throw new TransportProtocolException("Transport message exceeds maximum size");
		}
		return json;
	}

	public TransportMessage decode(String json)
	{
		if (json == null)
		{
			throw new TransportProtocolException("Transport message must not be null");
		}
		if (json.length() > TransportProtocol.MAX_MESSAGE_CHARS)
		{
			throw new TransportProtocolException("Transport message exceeds maximum size");
		}

		final JsonElement parsed;
		try
		{
			parsed = new JsonParser().parse(json);
		}
		catch (RuntimeException ex)
		{
			throw new TransportProtocolException("Transport message is not valid JSON", ex);
		}

		if (parsed == null || !parsed.isJsonObject())
		{
			throw new TransportProtocolException("Transport message root must be an object");
		}

		JsonObject root = parsed.getAsJsonObject();
		String protocol = requireString(root, "protocol");
		int version = requireInt(root, "version");
		if (!TransportProtocol.NAME.equals(protocol))
		{
			throw new TransportProtocolException("Unsupported transport protocol: " + protocol);
		}
		if (version != TransportProtocol.VERSION)
		{
			throw new TransportProtocolException(
				"Unsupported transport protocol version: " + version
			);
		}

		TransportMessage.Kind kind = kindFromWire(requireString(root, "kind"));
		JsonObject payload = requireObject(root, "payload");
		switch (kind)
		{
			case HELLO:
				return new TransportMessage.Hello(
					requireString(payload, "source"),
					requireString(payload, "eventProtocol"),
					requireInt(payload, "eventVersion"),
					requireCapabilities(payload, "capabilities")
				);
			case HELLO_ACK:
				return new TransportMessage.HelloAck(
					requireString(payload, "eventProtocol"),
					requireInt(payload, "eventVersion"),
					requireCapabilities(payload, "capabilities")
				);
			case EVENT:
				return new TransportMessage.Event(
					decodeEvent(requireObject(payload, "event"))
				);
			case STATE:
				return new TransportMessage.State(
					decodeEvent(requireObject(payload, "event"))
				);
			case RESET:
				return new TransportMessage.Reset(requireString(payload, "source"));
			case ERROR:
				return new TransportMessage.Error(
					requireString(payload, "code"),
					requireString(payload, "message")
				);
			default:
				throw new TransportProtocolException("Unsupported transport message kind: " + kind);
		}
	}

	private JsonObject payload(TransportMessage message)
	{
		JsonObject payload = new JsonObject();
		if (message instanceof TransportMessage.Hello)
		{
			TransportMessage.Hello hello = (TransportMessage.Hello) message;
			payload.addProperty("source", hello.getSource());
			payload.addProperty("eventProtocol", hello.getEventProtocol());
			payload.addProperty("eventVersion", hello.getEventVersion());
			payload.add("capabilities", encodeCapabilities(hello.getCapabilities()));
		}
		else if (message instanceof TransportMessage.HelloAck)
		{
			TransportMessage.HelloAck ack = (TransportMessage.HelloAck) message;
			payload.addProperty("eventProtocol", ack.getEventProtocol());
			payload.addProperty("eventVersion", ack.getEventVersion());
			payload.add("capabilities", encodeCapabilities(ack.getCapabilities()));
		}
		else if (message instanceof TransportMessage.Event)
		{
			payload.add("event", encodeEvent(((TransportMessage.Event) message).getEvent()));
		}
		else if (message instanceof TransportMessage.State)
		{
			payload.add("event", encodeEvent(((TransportMessage.State) message).getEvent()));
		}
		else if (message instanceof TransportMessage.Reset)
		{
			payload.addProperty("source", ((TransportMessage.Reset) message).getSource());
		}
		else if (message instanceof TransportMessage.Error)
		{
			TransportMessage.Error error = (TransportMessage.Error) message;
			payload.addProperty("code", error.getCode());
			payload.addProperty("message", error.getMessage());
		}
		else
		{
			throw new TransportProtocolException(
				"Unsupported transport message implementation: " + message.getClass().getName()
			);
		}
		return payload;
	}

	private JsonObject encodeEvent(com.ashy0019.localeventbridge.event.LocalEvent event)
	{
		return new JsonParser().parse(eventCodec.encode(event)).getAsJsonObject();
	}

	private com.ashy0019.localeventbridge.event.LocalEvent decodeEvent(JsonObject object)
	{
		return eventCodec.decode(gson.toJson(object));
	}

	private static JsonArray encodeCapabilities(Set<SourceCapability> capabilities)
	{
		JsonArray array = new JsonArray();
		for (SourceCapability capability : SourceCapability.values())
		{
			if (capabilities.contains(capability))
			{
				array.add(capability.getWireName());
			}
		}
		return array;
	}

	private static Set<SourceCapability> requireCapabilities(JsonObject object, String name)
	{
		JsonElement value = require(object, name);
		if (!value.isJsonArray())
		{
			throw new TransportProtocolException("Field must be an array: " + name);
		}
		EnumSet<SourceCapability> capabilities = EnumSet.noneOf(SourceCapability.class);
		for (JsonElement item : value.getAsJsonArray())
		{
			if (!item.isJsonPrimitive() || !item.getAsJsonPrimitive().isString())
			{
				throw new TransportProtocolException("Capability values must be strings");
			}
			SourceCapability capability = SourceCapability.fromWire(item.getAsString());
			if (!capabilities.add(capability))
			{
				throw new TransportProtocolException(
					"Duplicate source capability: " + capability.getWireName()
				);
			}
		}
		return capabilities;
	}

	private static String kindToWire(TransportMessage.Kind kind)
	{
		switch (kind)
		{
			case HELLO:
				return "hello";
			case HELLO_ACK:
				return "hello_ack";
			case EVENT:
				return "event";
			case STATE:
				return "state";
			case RESET:
				return "reset";
			case ERROR:
				return "error";
			default:
				throw new TransportProtocolException("Unsupported transport kind: " + kind);
		}
	}

	private static TransportMessage.Kind kindFromWire(String value)
	{
		switch (normalize(value))
		{
			case "hello":
				return TransportMessage.Kind.HELLO;
			case "hello_ack":
				return TransportMessage.Kind.HELLO_ACK;
			case "event":
				return TransportMessage.Kind.EVENT;
			case "state":
				return TransportMessage.Kind.STATE;
			case "reset":
				return TransportMessage.Kind.RESET;
			case "error":
				return TransportMessage.Kind.ERROR;
			default:
				throw new TransportProtocolException("Unsupported transport message kind: " + value);
		}
	}

	private static String normalize(String value)
	{
		return value.trim().toLowerCase(Locale.ROOT);
	}

	private static JsonElement require(JsonObject object, String name)
	{
		JsonElement value = object.get(name);
		if (value == null || value.isJsonNull())
		{
			throw new TransportProtocolException("Missing required field: " + name);
		}
		return value;
	}

	private static JsonObject requireObject(JsonObject object, String name)
	{
		JsonElement value = require(object, name);
		if (!value.isJsonObject())
		{
			throw new TransportProtocolException("Field must be an object: " + name);
		}
		return value.getAsJsonObject();
	}

	private static String requireString(JsonObject object, String name)
	{
		JsonElement value = require(object, name);
		if (!value.isJsonPrimitive())
		{
			throw new TransportProtocolException("Field must be a string: " + name);
		}
		JsonPrimitive primitive = value.getAsJsonPrimitive();
		if (!primitive.isString())
		{
			throw new TransportProtocolException("Field must be a string: " + name);
		}
		return primitive.getAsString();
	}

	private static int requireInt(JsonObject object, String name)
	{
		JsonElement value = require(object, name);
		if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber())
		{
			throw new TransportProtocolException("Field must be numeric: " + name);
		}
		try
		{
			return new BigDecimal(value.getAsString()).intValueExact();
		}
		catch (ArithmeticException | NumberFormatException ex)
		{
			throw new TransportProtocolException("Field must be an integer: " + name, ex);
		}
	}
}
