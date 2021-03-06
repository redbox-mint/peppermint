package scripts.jsonldparse;

import groovy.json.*

class utils {

  static renameIds ( v ) {
    if (v instanceof Map) {
      def remapped = [:]
      v.each { k, val ->
        if (k == '@id') {
          remapped['id'] = val
        } else {
          remapped[k] = renameIds(val)
        }
      }
      return remapped
    } else {
      return v
    }
  }

  static trim (vals ) {
  	if (vals instanceof List && vals.size() > 0) {
  		return vals.collect { it instanceof String ?  it.trim() : it }
  	}
  	return vals instanceof String ? vals.trim() : vals
  }

  static enforceSolrFieldNames (k) {
  	return k.replaceAll(/[^a-zA-Z\d_]/, '_')
  }

  static ensureValidId (val) {
    return val.replaceAll(/\s/, '_')
  }

  static addKvToDocument ( solrField, k, v, document ) {
    if (k == '@type') {
      document['type'] = v
  		solrField = 'type'
    } else if (k == '@id') {
      document['id'] = v
  		solrField = 'id'
    } else {
  		document[solrField] = renameIds(v)
  	}
  }

  static getGraphEntry ( data, id) {
    return data['@graph'].find { entry ->
      entry['@id'] == id
    }
  }

  static addKvAndFacetsToDocument (data, k, v, docs, facetDoc, recordTypeConfig, entryTypeFieldName) {
    def slurper = new JsonSlurper()
    def solrField = enforceSolrFieldNames(k)
    if (k == '@type') {
      def typeVal = v
      if (v instanceof java.util.ArrayList) {
        // select the last one...
        typeVal = v[v.size()-1]
      }
      // select the type label as the last one...
      def vparts = typeVal.split('/')
      def typeLabel  = vparts[vparts.length - 1]
      docs.each { doc ->
        doc['type'] = v
        doc['type_label'] = typeLabel;
      }
  		solrField = 'type'
    } else if (k == '@id') {
      docs.each { doc ->
        doc['id'] = v
      }
  		solrField = 'id'
    } else {
      if (v instanceof Map || v instanceof Map.Entry || v instanceof List) {
        def expanded = null
        if ((v instanceof Map && v.hasProperty('@id') && v['@id']) || (v instanceof Map.Entry && v.key == '@id' && v.value)) {
          def v_id = v.hasProperty('@id') ? v['@id'] : v.value;
          expanded = getGraphEntry(data, v_id)
          docs.each { doc ->
            if (expanded) {
              if (v instanceof Map.Entry) {
                doc[solrField] = ['@id': v_id ] << expanded
              } else {
                doc[solrField] = v << expanded
              }
            } else {
              doc[solrField] = renameIds(v)
            }
          }
        } else {
          v.each {vEntry ->
            if (vEntry instanceof Map && vEntry['@id']) {
              expanded = getGraphEntry(data, vEntry['@id'])
              if (expanded && expanded instanceof Map) {
                vEntry << expanded
              } else if (expanded instanceof String) {
                vEntry << slurper.parseText(expanded)
              }
            }
          }
          docs.each { doc ->
            doc[solrField] = renameIds(v)
          }
        }
      } else {
        docs.each { doc ->
          doc[solrField] = renameIds(v)
        }
      }
  	}

    def facetConfig = recordTypeConfig.facets[k]
  	if (facetConfig) {
  		def vals = null
      def val = v
      if (facetConfig.fieldName) {
        if (v instanceof Map && v.containsKey(facetConfig.fieldName) ) {
          val = v[facetConfig.fieldName]
        } else if (v instanceof Map.Entry && v.key == facetConfig.fieldName) {
          val = v.value
        }
      }
  		if (facetConfig.tokenize) {
  			vals = val ? val.tokenize(facetConfig.tokenize.delim) : val
  		} else {
  			vals = val
  		}
  		if (facetConfig.trim) {
  			vals =  trim(vals)
  		}
  		if (facetConfig.escape_value == "solr_field") {
  			if (vals instanceof List) {
  				vals = vals.collect { enforceSolrFieldNames(it) }
  			} else {
  				vals = enforceSolrFieldNames(vals)
  			}
  		}
  		def suffix = "facet"
  		if (facetConfig.field_suffix) {
  			suffix = facetConfig.field_suffix
  		}
  		if (facetConfig.skip_entry_type_suffix) {
  			suffix = "_______${suffix}"
  		} else {
  			suffix = "_______${entryTypeFieldName}_______${suffix}"
  		}
  		facetDoc["${solrField}${suffix}"] = vals
  	}
  }
}
