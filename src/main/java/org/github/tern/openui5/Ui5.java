package org.github.tern.openui5;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import javax.json.Json;
import javax.json.JsonArray;
import javax.json.JsonObject;
import javax.json.JsonObjectBuilder;
import javax.json.JsonReader;
import javax.json.JsonValue;
import javax.json.JsonValue.ValueType;
import javax.json.JsonWriter;
import javax.json.stream.JsonGenerator;

public class Ui5 {
	private static final String UI5_API_URL = "https://openui5.hana.ondemand.com/#/api/";
	private static final String UI5_METADATA_URL = "https://openui5.hana.ondemand.com/resources/sap-ui-version.json";
	private static final String UI5_LIB_URL = "https://openui5.hana.ondemand.com/test-resources/{1}/designtime/api.json";
	
	private static final Logger logger = Logger.getLogger(Ui5.class.getName());

	// Object tree
	Map<String, Object> paths = new HashMap<>();

	public static void main(String[] args) throws Exception {
		FileOutputStream fos = new FileOutputStream("c:\\tmp\\openui5.js");
		fos.write(Ui5.generateOpenUi5());
		fos.close();
	}

	/**
	 * Parse class
	 * 
	 * @param oIn
	 *          class Object
	 * @return JsonObject of this class
	 */
	private void parseClass(JsonObject oIn) {
		// Class
		JsonObjectBuilder oOut = Json.createObjectBuilder();
		if (oIn.getJsonString("description") != null) {
			oOut.add("!doc", formatDoc(oIn.getString("description")));
		}
		String url = UI5_API_URL + oIn.getString("name");
		oOut.add("!url", url);

		JsonObjectBuilder prototype = Json.createObjectBuilder();
		if (oIn.getJsonString("extends") != null) {
			prototype.add("!proto", oIn.getString("extends") + ".prototype");
		}

		// Constructor
		if (oIn.getJsonObject("constructor") != null) {
			buildFn(oIn.getJsonObject("constructor"), oOut);
		}

		// properties
		if (oIn.getJsonObject("ui5-metadata") != null
		    && oIn.getJsonObject("ui5-metadata").getJsonArray("properties") != null) {
			for (JsonValue array : oIn.getJsonObject("ui5-metadata").getJsonArray("properties")) {
				if (array.getValueType() == ValueType.OBJECT) {
					JsonObject property = (JsonObject) array;
					prototype.add(property.getString("name"), parseProperty(property, url, null));
				}
			}
		}

		// methods
		if (oIn.getJsonArray("methods") != null) {
			for (JsonValue array : oIn.getJsonArray("methods")) {
				if (array.getValueType() == ValueType.OBJECT) {
					JsonObject method = (JsonObject) array;
					prototype.add(method.getString("name"), parseMethod(method, url));
				}
			}
		}

		oOut.add("prototype", prototype);

		adddObject(oIn.getString("name"), oOut.build());
	}

	@SuppressWarnings("unchecked")
	private void adddObject(String name, Object obj) {
		Map<String, Object> curr = paths;
		String[] parents = name.split("\\.");
		for (int i = 0; i < parents.length; i++) {
			String key = parents[i];
			if (i == parents.length - 1) {
				try {
					curr.put(key, obj);
				} catch (Exception ex) {
					//
				}
			} else {
				if (curr.get(key) == null) {
					Map<String, Object> child = new HashMap<>();
					curr.put(key, child);
				}

				curr = (Map<String, Object>) curr.get(key);
			}
		}
	}

	private String formatDoc(String doc) {
		return doc.replace("\n", " ");
	}

	private JsonObjectBuilder parseProperty(JsonObject oIn, String url, Object object) {
		JsonObjectBuilder oOut = Json.createObjectBuilder();
		if (oIn.getJsonString("description") != null) {
			oOut.add("!doc", formatDoc(oIn.getString("description")));
		}

		oOut.add("!type", convertTypeToTern(oIn.getString("type")));

		return oOut;
	}

	/**
	 * Parse method
	 * 
	 * @param oIn
	 *          method object
	 * @param url
	 *          url of class
	 * @return JsonObject of this method
	 */
	private JsonObjectBuilder parseMethod(JsonObject oIn, String url) {
		JsonObjectBuilder oOut = Json.createObjectBuilder();
		if (oIn.getJsonString("description") != null) {
			oOut.add("!doc", formatDoc(oIn.getString("description")));
		}
		if (oIn.getJsonString("name") == null) {
			oOut.add("!url", url);
		} else {
			oOut.add("!url", url + "/methods/" + oIn.getString("name"));
		}

		buildFn(oIn, oOut);

		return oOut;
	}

	private void buildFn(JsonObject oIn, JsonObjectBuilder oOut) {
		String returnValue = "";
		if (oIn.getJsonObject("returnValue") != null && oIn.getJsonObject("returnValue").getJsonString("type") != null) {
			returnValue = " -> " + convertTypeToTern(oIn.getJsonObject("returnValue").getString("type"));
		}

		String fn = "fn(";
		boolean first = true;
		if (oIn.getJsonArray("parameters") != null) {
			for (JsonValue array : oIn.getJsonArray("parameters")) {
				if (array.getValueType() == ValueType.OBJECT) {
					JsonObject parameter = (JsonObject) array;
					if (first) {
						first = false;
					} else {
						fn += ", ";
					}
					String optional = parameter.getBoolean("optional", true) ? "?" : "";
					fn += convertNameToTern(parameter.getString("name")) + optional + ": "
					    + convertTypeToTern(parameter.getString("type"));
				}
			}
		}
		fn += ")";

		oOut.add("!type", fn + returnValue);
	}

	/**
	 * Convert ui5 name to Tern anem
	 * 
	 * @param name
	 *          ui5 name
	 * @return Tern name
	 */

	private String convertNameToTern(String name) {
		// Remove tag in name
		return name.replaceAll("<[^>]*>", "");
	}

	/**
	 * Convert ui5 type to Tern type
	 * 
	 * @param type
	 *          ui5 type
	 * @return Tern type
	 */
	private String convertTypeToTern(String type) {
		// If we have several possibilities, we take the first one.
		if (type.split("\\|").length > 1) {
			type = type.split("\\|")[0];
		}

		// If we have somthing like this "fn(oEvents: Object.<string,function()>)"
		// we replace by ?
		if (type.contains("<") || type.contains(">")) {
			type = "?";
		}

		// Array
		if (type.endsWith("[]")) {
			type = "[" + type.replace("[]", "") + "]";
		}

		if ("int".equals(type))
			type = "number";
		if ("boolean".equals(type))
			type = "bool";
		if ("*".equals(type))
			type = "?";

		return type;
	}

	/**
	 * Parse a library
	 * 
	 * @param url
	 * @throws Exception
	 */
	private void parseLib(String url) throws Exception {
		try {
			JsonObject json = loadLib(url);

			JsonArray jsonArray = json.getJsonArray("symbols");

			paths.put("", new HashMap<String, Object>());

			for (JsonValue value : jsonArray) {
				try {
					if (value.getValueType() == ValueType.OBJECT) {
						JsonObject obj = (JsonObject) value;
						if ("class".equals(obj.getString("kind"))) {
							parseClass(obj);
						} else if ("enum".equals(obj.getString("kind"))) {
							parseEnum(obj);
						} else if ("interface".equals(obj.getString("kind"))) {
							parseInterface(obj);
						}
					}
				} catch (Exception ex) {
					//We don't block in this case, just a log to analyse
					logger.log(Level.SEVERE, "Error on "+value.toString(), ex);
				}
			}
		} catch (Exception ex) {
			// Ignore, some libraries are not present
		}
	}

	private static JsonObject loadLib(String url) throws IOException, MalformedURLException {
		HttpURLConnection con = (HttpURLConnection) (new URL(url)).openConnection();

		InputStream is = con.getInputStream();
		JsonReader jsonReader = Json.createReader(new BufferedInputStream(is));
		JsonObject json = jsonReader.readObject();
		jsonReader.close();
		is.close();
		return json;
	}

	/**
	 * Parse interface
	 * 
	 * @param oIn
	 *          interface Object
	 * @return JsonObject of this interface
	 */

	private void parseInterface(JsonObject oIn) {
		// Interface
		JsonObjectBuilder oOut = Json.createObjectBuilder();
		if (oIn.getJsonString("description") != null) {
			oOut.add("!doc", formatDoc(oIn.getString("description")));
		}
		String url = UI5_API_URL + oIn.getString("name");
		oOut.add("!url", url);

		adddObject(oIn.getString("name"), oOut.build());
	}

	/**
	 * Parse enum
	 * 
	 * @param oIn
	 *          enum Object
	 * @return JsonObject of this enum
	 */

	private void parseEnum(JsonObject oIn) {
		// Enum
		JsonObjectBuilder oOut = Json.createObjectBuilder();
		if (oIn.getJsonString("description") != null) {
			oOut.add("!doc", formatDoc(oIn.getString("description")));
		}
		String url = UI5_API_URL + oIn.getString("name");
		oOut.add("!url", url);

		JsonObjectBuilder prototype = Json.createObjectBuilder();

		// properties
		if (oIn.getJsonArray("properties") != null) {
			for (JsonValue array : oIn.getJsonArray("properties")) {
				if (array.getValueType() == ValueType.OBJECT) {
					JsonObject property = (JsonObject) array;
					prototype.add(property.getString("name"), parseProperty(property, url, null));
				}
			}
		}

		oOut.add("prototype", prototype);

		adddObject(oIn.getString("name"), oOut.build());
	}

	@SuppressWarnings("unchecked")
	private void write(JsonObjectBuilder jsonObjectBuilder, Map<String, Object> node) {
		for (String key : node.keySet()) {
			if (node.get(key) instanceof JsonObject) {
				jsonObjectBuilder.add(key, (JsonObject) node.get(key));
			} else {
				JsonObjectBuilder child = Json.createObjectBuilder();
				write(child, (Map<String, Object>) node.get(key));
				if (key.length() > 0) {
					jsonObjectBuilder.add(key, child);
				}
			}
		}
	}
	
	public static byte[] generateOpenUi5() throws Exception {
		return new Ui5().parse();
	}

	/**
	 * Get openUi5 metadata
	 * 
	 * @return JsonObject metadata
	 */
	public static JsonObject getMetadata() throws Exception {
		return loadLib(UI5_METADATA_URL);
	}

	/**
	 * Parse all libraries
	 * @return 
	 */
	private byte[] parse() throws Exception {
		JsonObject json = getMetadata();

		// Parse all libraries
		for (JsonValue value : json.getJsonArray("libraries")) {
			JsonObject library = (JsonObject) value;
			if (library.getString("name").startsWith("sap.")) {
			  parseLib(UI5_LIB_URL.replace("{1}", library.getString("name").replace(".", "/")));
			}
		}
		
		JsonObjectBuilder root = Json.createObjectBuilder();
		root.add("!name", "openui5");

		write(root, paths);

		ByteArrayOutputStream osJson = new ByteArrayOutputStream();
		JsonWriter jsonWriter = Json.createWriterFactory(Collections.singletonMap(JsonGenerator.PRETTY_PRINTING, true))
		    .createWriter(osJson);
		jsonWriter.writeObject(root.build());
		jsonWriter.close();
		
		String result = new BufferedReader(new InputStreamReader(Ui5.class.getResourceAsStream("openui5-template.js"))).lines().collect(Collectors.joining("\n"));
		
		return result.replace("{{defs}}", osJson.toString()).getBytes();
	}
}
