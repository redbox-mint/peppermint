/**
 * JSON-LD Parser for Solr.
 *
 * Works with commit-to-solr.groovy.
 */
@Grapes([
	@Grab(group='com.github.jsonld-java', module='jsonld-java', version='0.12.0')
])
import java.util.*;
import java.io.*;
import groovy.json.*;
import com.github.jsonldjava.core.*;
import com.github.jsonldjava.utils.*;
import javax.script.*;
//-------------------------------------------------------
// Init, executed once to grab dependencies
//-------------------------------------------------------
try {
	if (initRun) {
		println "JSON LD Main Parser, init okay."
		return
	}
} catch (e) {
	// swallowing
}
//-------------------------------------------------------
// Script Fns
//-------------------------------------------------------

def loadScript(s) {
	if (!compiledScripts[s]) {
		def reader = new FileReader(s)
		compiledScripts[s] = ((Compilable)engine).compile(reader)
		reader.close()
	} else {
		logger.info("Using cached version of: ${s}")
	}
}

def processEntry(manager, engine, entry, type, useDefaultHandler) {
	def sw = new StringWriter()
	def pw = new PrintWriter(sw, true)

	def script = recordTypeConfig.types[type];
	manager.getBindings().put('entry', entry)
	manager.getBindings().put('entryType', type)
	if (script) {
		// assumes groovy for now
		try {
			script.each { s ->
				loadScript(s)
				compiledScripts[s].eval(manager.getBindings())
			}
		} catch (e) {
			logger.error("Failed to run: ${script}");
			logger.error(e)
			throw e
		}
	} else {
		if (useDefaultHandler) {
			logger.debug("No script configured for type: ${type}, running default handler if configured, ignoring if not.");
			script = recordTypeConfig['defaultHandlerScript']
			if (script) {
				try {
					loadScript(script)
					compiledScripts[script].eval(manager.getBindings())
				} catch (e) {
					logger.error("Failed to run: ${script}");
					logger.error(e)
					e.printStackTrace(pw)
					logger.error("Stack trace:  ")
					logger.error(sw.toString())
					throw e
				}
			}
		} else {
			logger.error("No script configured for: ${type}, and not using default handler, ignoring.")
		}
	}
}

def ensureSchemaOrgHttps(data) {
	def newContext = [:]
	data['@context'].each {key, val ->
		newContext[key] = val.replaceAll('http://schema.org', 'https://schema.org')
	}
	data['@context'] = newContext
}

boolean isCollectionOrArray(object) {
    [Collection, Object[]].any { it.isAssignableFrom(object.getClass()) }
}

def ensureIdsAreCleanAndShinyAndNiceAndWonderfulIRIs(data) {
	def modified = []
	data.eachWithIndex {entry, idx ->
		def modEntry = [:]
		if (entry instanceof Map || isCollectionOrArray(entry)) {
			entry.each {key, val ->
				if (key == '@id') {
					modEntry['id_orig'] = val
					modEntry['@id'] = val.replaceAll(/\s/, '_')
				} else
				if (isCollectionOrArray(val) || val instanceof Map) {
					modEntry[key] = ensureIdsAreCleanAndShinyAndNiceAndWonderfulIRIs(val)
				} else {
					modEntry[key] = val
				}
			}
		} else {
			modEntry = entry
		}

		modified[idx] = modEntry
	}
	return modified
}
//-------------------------------------------------------
// Start of Script
//-------------------------------------------------------
def docList = []
def document = [:]
// put the document list
manager.getBindings().put('docList', docList)
manager.getBindings().put('document', document)
def compiledScripts = manager.getBindings().get('compiledScripts')

ensureSchemaOrgHttps(data)
// WARNING: the config setting below will remove spaces in the @id fields (saving the original value in 'id_orig').
// This may result in a broken IRI. Explicitly set `config.disableCleanOfIds` to true to prevent this.
if (!config.disableCleanupOfIds) {
	data['@graph'] = ensureIdsAreCleanAndShinyAndNiceAndWonderfulIRIs(data['@graph'])
}

def slurper = new JsonSlurper()
// def jsonStr = JsonOutput.toJson(data['@graph'])
def context = [:]
// document['raw_json_t'] =  jsonStr
recordTypeConfig = config['recordType'][recordType]
manager.getBindings().put('recordTypeConfig', recordTypeConfig)
document['record_type_s'] = recordType
document["record_format_s"] = recordTypeConfig['format']
document['_childDocuments_'] = []

JsonLdOptions options = new JsonLdOptions()
def compacted = null
try {
	compacted = JsonLdProcessor.compact(data, context, options);
} catch (IllegalArgumentException e) {
	logger.error("Exception in parsing JSONLD, make sure your '@id' values are valid IRIs, etc. See stacktrace below for more information.")
	// bubble up the exception, so stack trace can be printed...
	throw e
}
if (compacted['@graph']) {
	// find the root node of the graph...
	def rootNodeId = recordTypeConfig['rootNodeFieldContextId']
	def rootNodeVals = recordTypeConfig['rootNodeFieldValues'];
	logger.info("Using rootNodeId:" + rootNodeId)
	def rootNode = data['@graph'].find {
		return it[rootNodeId] instanceof Collection ? rootNodeVals.intersect(it[rootNodeId]).size() > 0 : rootNodeVals.contains(it[rootNodeId]) // it[rootNodeId] == 'data/' || it[rootNodeId]== './'
	}
	def parentDoc = [:]
	manager.getBindings().put("parentLinkDoc", parentDoc)
	parentDoc["@id"] = rootNode["@id"]
	parentDoc["name"] = rootNode["name"]
	compacted['@graph'].each { entry ->
		if (entry['@id'] == rootNode['@id']) {
			processEntry(manager, engine, entry, 'rootNode', false)
		} else {
			def type = entry['@type']
			if (type instanceof Collection) {
				type.each { t ->
					processEntry(manager, engine, entry, t, true)
				}
			} else {
				processEntry(manager, engine, entry, type, true)
			}
		}
	}
}
// document["raw_compacted_t"] = JsonOutput.toJson(compacted)
document["date_updated_dt"] = new Date()
docList << [document: document, core: recordTypeConfig.core]
return docList
