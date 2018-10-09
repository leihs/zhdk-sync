# leihs_zhdk-sync

### Deploy

example:

    ansible-playbook -v -i ../zhdk-inventory/developer-hosts --limit tom deploy/deploy_play.yml

### API Notes

curl -u $ZAPI_TOKEN_USER https://zapi.zhdk.ch/v1/documentation | json_pp

curl -u $ZAPI_TOKEN_USER https://zapi.zhdk.ch/v1/person/documentation | json_pp

curl -u $ZAPI_TOKEN_USER https://zapi.zhdk.ch/v1/person/?limit=1 | json_pp

curl -s -u "$ZAPI_TOKEN_USER:" 'https://zapi.zhdk.ch/v1/person/?limit=1&last_name=schank&fieldsets=basic,personal_contact,leihs_temp' | json_pp


### General Documentation

{
   "data structure" : "All data is structured as array with \"pagination_info\" \"data\" as their top level objects. The resourcess's data is stored in the object \"data\" which is always an array. Each resource's data includes \"id\" and \"type\" in the top level and may contain a link. All further data is grouped by \"fieldsets\", e.g.\n{\n  \"pagination_info\": {\n    \"offset\": 0,\n    \"limit\": 50\n  },\n  \"data\": [\n    {\n      \"id\": 186835,\n      \"type\": \"person\",\n      \"basic\": {\n        \"last_name\": \"Rohrer\",\n        \"first_name\": \"Beat\",\n        \"title\": \"\",\n        \"company\": \"\",\n        \"additional_info\": \"\",\n        \"url\": \"https://lamp-zhdk/person/186835\",\n        \"is_zhdk\": true\n      }\n    }\n  ]\n}",
   "authentication" : "You must request an account to get access to ZAPI with your client application. You may request several API keys which must be included in every request as HTTP Basic Auth in the user field. The password field is ignored.",
   "fieldsets" : "All ZAPI resources have a parameter fieldsets. You may indicate with this parameter which data you need for the resource.\n\n- the default fieldset is \"default\" and contains only information about the id, the resource type and the resource URI\n- you may request several comma separated fieldset names\n- please consult the resource's documentation for a list of available fieldsets that are available to you\n- a fieldset's data can be found in an object with the fieldset's name as key",
   "general" : "ZAPI is a RESTful API. All data and permissions are organised by resources and the API makes use of the HTTP standards wherever possible. You may append \"/documentation\" to any resource to get the documentation for that resource, e.g. https://zapi.zhdk.ch/v1/person/documentation.. All request parameters (i.e. all filters, \"offset\", \"limit\", \"order_by\", and \"fieldsets\") have to be set as URL paramters.\n\nIf you request a single resoruce in the form zapi/v1/person/1234 and this resource is not found, you'll receive the HTTP status 404. If you don't request a single resource and nothing is found, you'll receive the HTTP status 200 but the \"data\" will only contain an empty array.",
   "permissions" : "To access a field set of a resource, you need explicit permission to do so.",
   "filter" : "The documentation of each resource includes a list of available filters that can be applied. Depending on your access permissions, you may be required to apply certain filers for some resources.\n\nIf a single filter allows several values, they're always combined using an OR-conjunction, but several distinct filters are always combined using an AND-conjunction.",
   "pagination" : "All results are paginated. You may use the URL paramaters \"limit\" and \"offset\" to loop through all results for a given request. The default limit is 50 and the maximum is 100.\n\nDetails about the paginaton can be found in the key 'pagination_info' in the root object. The keys 'offset' and 'limit' are always set, the key 'result_count' is set if the information is available and indicates the total number of results for the applied filters without the limit. If you need the information about the total number of results for a resource where it's not available, please contact the ZHdK development team.",
   "data format" : "You may set the desired data format and charset in the HTTP Accept Header. The defauls are UTF-8 and application/json. All the supported formats are application/json, and text/html; all the supported charsets are utf-8. Please note that the syntax with only one wildcard is not supported, e.g. \"text/*\", but \"*/*\" is.",
   "date format" : "All dates and date times are formatted as ISO 8601."
}

### Person Documentation

curl -u $ZAPI_TOKEN_USER https://zapi.zhdk.ch/v1/person/documentation | json_pp

{
   "resource" : {
      "description" : "This resource represents a person. \r\nThe ID is the Evento ID.",
      "name" : "person"
   },
   "available fieldsets" : {
      "user_group" : {
         "accessible" : "yes",
         "name" : "user_group",
         "description" : "user group membership information\r\n\r\nNOTE: this is an expensive query. Please use only where necessary."
      },
      "business_contact" : {
         "accessible" : "yes",
         "name" : "business_contact",
         "description" : "business contact data"
      },
      "leihs_temp" : {
         "accessible" : "yes",
         "name" : "leihs_temp",
         "description" : "Temp field set for library user id (nebis)  and matriculation number. the fieldset will be replaced with a proper fieldset 'in the near future'. name of the fieldset has not been defined yet."
      },
      "personal_contact" : {
         "accessible" : "yes",
         "name" : "personal_contact",
         "description" : "Institutional and private contact information (address, mail, phone). Privacy settings are ignored."
      },
      "basic" : {
         "accessible" : "yes",
         "name" : "basic",
         "description" : "basic data, including name"
      },
      "photo" : {
         "description" : "Contains links to a person's photos and information about the public availability of a person's photos. Please use the photo API if you need different formats: https://intern.zhdk.ch/?person/foto",
         "accessible" : "yes",
         "name" : "photo"
      }
   },
   "available filters" : {
      "name_fulltext" : {
         "name" : "name_fulltext",
         "accessible" : "yes",
         "description" : "Searches in first name, last name and evento id"
      },
      "first_name" : {
         "description" : "filter by first name",
         "name" : "first_name",
         "accessible" : "yes"
      },
      "only_zhdk" : {
         "description" : "Only persons who are part of the ZHdK. Be aware that this is only a flag and any value assigned to it is ignored.",
         "name" : "only_zhdk",
         "accessible" : "yes"
      },
      "only_public" : {
         "description" : "This flag reduces the result set to persons, who have set their privacy settings, so they can be show in a public context. Please note: this is a flag and the value for this parameter doesn't matter.",
         "accessible" : "yes",
         "name" : "only_public"
      },
      "ids" : {
         "accessible" : "yes",
         "name" : "ids",
         "description" : "comma-separated list of ids"
      },
      "last_name" : {
         "description" : "filter by last name",
         "accessible" : "yes",
         "name" : "last_name"
      }
   },
   "order_by" : "no sorting available",
   "limit" : "Limit the results; default: 50, max: 100",
   "offset" : "set the result offset"
}



### User-Group 


