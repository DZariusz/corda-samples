/**
 * 
 * No description provided (generated by Openapi Generator https://github.com/openapitools/openapi-generator)
 *
 * The version of the OpenAPI document: 1.0.0
 * 
 *
 * NOTE: This class is auto generated by OpenAPI Generator (https://openapi-generator.tech).
 * https://openapi-generator.tech
 * Do not edit the class manually.
 *
 */

import ApiClient from '../ApiClient';

/**
 * The NetCordaCoreIdentityParty model module.
 * @module io.generated.model/NetCordaCoreIdentityParty
 * @version 1.0.0
 */
class NetCordaCoreIdentityParty {
    /**
     * Constructs a new <code>NetCordaCoreIdentityParty</code>.
     * @alias module:io.generated.model/NetCordaCoreIdentityParty
     * @param name {String} CordaX500Name encoded Party
     * @param owningKey {String} Base 58 Encoded Public Key
     */
    constructor(name, owningKey) { 
        
        NetCordaCoreIdentityParty.initialize(this, name, owningKey);
    }

    /**
     * Initializes the fields of this object.
     * This method is used by the constructors of any subclasses, in order to implement multiple inheritance (mix-ins).
     * Only for internal use.
     */
    static initialize(obj, name, owningKey) { 
        obj['name'] = name;
        obj['owningKey'] = owningKey;
    }

    /**
     * Constructs a <code>NetCordaCoreIdentityParty</code> from a plain JavaScript object, optionally creating a new instance.
     * Copies all relevant properties from <code>data</code> to <code>obj</code> if supplied or a new instance if not.
     * @param {Object} data The plain JavaScript object bearing properties of interest.
     * @param {module:io.generated.model/NetCordaCoreIdentityParty} obj Optional instance to populate.
     * @return {module:io.generated.model/NetCordaCoreIdentityParty} The populated <code>NetCordaCoreIdentityParty</code> instance.
     */
    static constructFromObject(data, obj) {
        if (data) {
            obj = obj || new NetCordaCoreIdentityParty();

            if (data.hasOwnProperty('name')) {
                obj['name'] = ApiClient.convertToType(data['name'], 'String');
            }
            if (data.hasOwnProperty('owningKey')) {
                obj['owningKey'] = ApiClient.convertToType(data['owningKey'], 'String');
            }
        }
        return obj;
    }


}

/**
 * CordaX500Name encoded Party
 * @member {String} name
 */
NetCordaCoreIdentityParty.prototype['name'] = undefined;

/**
 * Base 58 Encoded Public Key
 * @member {String} owningKey
 */
NetCordaCoreIdentityParty.prototype['owningKey'] = undefined;






export default NetCordaCoreIdentityParty;

