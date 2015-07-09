/*
       Licensed to the Apache Software Foundation (ASF) under one
       or more contributor license agreements.  See the NOTICE file
       distributed with this work for additional information
       regarding copyright ownership.  The ASF licenses this file
       to you under the Apache License, Version 2.0 (the
       "License"); you may not use this file except in compliance
       with the License.  You may obtain a copy of the License at

         http://www.apache.org/licenses/LICENSE-2.0

       Unless required by applicable law or agreed to in writing,
       software distributed under the License is distributed on an
       "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
       KIND, either express or implied.  See the License for the
       specific language governing permissions and limitations
       under the License.
*/

package org.apache.cordova.contacts;

import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import org.apache.cordova.CordovaInterface;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.content.ContentProviderOperation;
import android.content.ContentProviderResult;
import android.content.ContentValues;
import android.content.OperationApplicationException;
import android.database.Cursor;
import android.net.Uri;
import android.os.RemoteException;
import android.provider.ContactsContract;

import android.provider.ContactsContract.CommonDataKinds.Event;
import android.provider.ContactsContract.CommonDataKinds.Nickname;
import android.provider.ContactsContract.CommonDataKinds.Note;
import android.provider.ContactsContract.RawContacts;
import android.provider.ContactsContract.CommonDataKinds.StructuredName;
import android.provider.ContactsContract.CommonDataKinds.Phone;
import android.provider.ContactsContract.CommonDataKinds.Email;
import android.provider.ContactsContract.CommonDataKinds.Im;
import android.provider.ContactsContract.CommonDataKinds.Website;
import android.provider.ContactsContract.CommonDataKinds.Organization;
import android.provider.ContactsContract.CommonDataKinds.Relation;
import android.provider.ContactsContract.CommonDataKinds.StructuredPostal;
import android.provider.ContactsContract.CommonDataKinds.Photo;


import android.util.Log;

import android.util.Base64;
import android.provider.ContactsContract.RawContacts;
import android.provider.ContactsContract.RawContactsEntity;
import android.provider.BaseColumns;
import android.util.SparseArray;
import java.lang.NumberFormatException;




/**
 * An implementation of {@link ContactAccessor} that uses current Contacts API.
 * This class should be used on Eclair or beyond, but would not work on any earlier
 * release of Android.  As a matter of fact, it could not even be loaded.
 * <p>
 * This implementation has several advantages:
 * <ul>
 * <li>It sees contacts from multiple accounts.
 * <li>It works with aggregated contacts. So for example, if the contact is the result
 * of aggregation of two raw contacts from different accounts, it may return the name from
 * one and the phone number from the other.
 * <li>It is efficient because it uses the more efficient current API.
 * <li>Not obvious in this particular example, but it has access to new kinds
 * of data available exclusively through the new APIs. Exercise for the reader: add support
 * for nickname (see {@link android.provider.ContactsContract.CommonDataKinds.Nickname}) or
 * social status updates (see {@link android.provider.ContactsContract.StatusUpdates}).
 * </ul>
 */

public class ContactAccessorSdk5 extends ContactAccessor {

    /**
     * Keep the photo size under the 1 MB blog limit.
     */
    private static final long MAX_PHOTO_SIZE = 1048576;

    private static final String EMAIL_REGEXP = ".+@.+\\.+.+"; /* <anything>@<anything>.<anything>*/

    /**
     * A static map that converts the JavaScript property name to Android database column name.
     */
    private static final Map<String, String> dbMap = new HashMap<String, String>();

    static {
        dbMap.put("id", ContactsContract.Data.CONTACT_ID);
        dbMap.put("displayName", ContactsContract.Contacts.DISPLAY_NAME);
        dbMap.put("name", ContactsContract.CommonDataKinds.StructuredName.DISPLAY_NAME);
        dbMap.put("name.formatted", ContactsContract.CommonDataKinds.StructuredName.DISPLAY_NAME);
        dbMap.put("name.familyName", ContactsContract.CommonDataKinds.StructuredName.FAMILY_NAME);
        dbMap.put("name.givenName", ContactsContract.CommonDataKinds.StructuredName.GIVEN_NAME);
        dbMap.put("name.middleName", ContactsContract.CommonDataKinds.StructuredName.MIDDLE_NAME);
        dbMap.put("name.honorificPrefix", ContactsContract.CommonDataKinds.StructuredName.PREFIX);
        dbMap.put("name.honorificSuffix", ContactsContract.CommonDataKinds.StructuredName.SUFFIX);
        dbMap.put("nickname", ContactsContract.CommonDataKinds.Nickname.NAME);
        dbMap.put("phoneNumbers", ContactsContract.CommonDataKinds.Phone.NUMBER);
        dbMap.put("phoneNumbers.value", ContactsContract.CommonDataKinds.Phone.NUMBER);
        dbMap.put("emails", ContactsContract.CommonDataKinds.Email.DATA);
        dbMap.put("emails.value", ContactsContract.CommonDataKinds.Email.DATA);
        dbMap.put("addresses", ContactsContract.CommonDataKinds.StructuredPostal.FORMATTED_ADDRESS);
        dbMap.put("addresses.formatted", ContactsContract.CommonDataKinds.StructuredPostal.FORMATTED_ADDRESS);
        dbMap.put("addresses.streetAddress", ContactsContract.CommonDataKinds.StructuredPostal.STREET);
        dbMap.put("addresses.locality", ContactsContract.CommonDataKinds.StructuredPostal.CITY);
        dbMap.put("addresses.region", ContactsContract.CommonDataKinds.StructuredPostal.REGION);
        dbMap.put("addresses.postalCode", ContactsContract.CommonDataKinds.StructuredPostal.POSTCODE);
        dbMap.put("addresses.country", ContactsContract.CommonDataKinds.StructuredPostal.COUNTRY);
        dbMap.put("ims", ContactsContract.CommonDataKinds.Im.DATA);
        dbMap.put("ims.value", ContactsContract.CommonDataKinds.Im.DATA);
        dbMap.put("organizations", ContactsContract.CommonDataKinds.Organization.COMPANY);
        dbMap.put("organizations.name", ContactsContract.CommonDataKinds.Organization.COMPANY);
        dbMap.put("organizations.department", ContactsContract.CommonDataKinds.Organization.DEPARTMENT);
        dbMap.put("organizations.title", ContactsContract.CommonDataKinds.Organization.TITLE);
        dbMap.put("birthday", ContactsContract.CommonDataKinds.Event.CONTENT_ITEM_TYPE);
        dbMap.put("note", ContactsContract.CommonDataKinds.Note.NOTE);
        dbMap.put("photos.value", ContactsContract.CommonDataKinds.Photo.CONTENT_ITEM_TYPE);
        dbMap.put("urls", ContactsContract.CommonDataKinds.Website.URL);
        dbMap.put("urls.value", ContactsContract.CommonDataKinds.Website.URL);

        dbMap.put("dirty", ContactsContract.RawContacts.DIRTY);
        dbMap.put("deleted", ContactsContract.RawContacts.DELETED);
        dbMap.put("sourceId", ContactsContract.RawContacts.SOURCE_ID);
        dbMap.put("sync1", ContactsContract.RawContacts.SYNC1);
        dbMap.put("sync2", ContactsContract.RawContacts.SYNC2);
        dbMap.put("sync3", ContactsContract.RawContacts.SYNC3);
        dbMap.put("sync4", ContactsContract.RawContacts.SYNC4);



    }

    /**
     * Create an contact accessor.
     */
    public ContactAccessorSdk5(CordovaInterface context) {
        mApp = context;
    }

    /**
     * This method takes the fields required and search options in order to produce an
     * array of contacts that matches the criteria provided.
     * @param fields an array of items to be used as search criteria
     * @param options that can be applied to contact searching
     * @return an array of contacts
     */
    @Override
    public JSONArray search(JSONArray fields, JSONObject options) {
        // Get the find options
        String searchTerm = "";
        int limit = Integer.MAX_VALUE;
        boolean multiple = true;
        String accountType = null;
        String accountName = null;
        boolean allContacts = false;

        if (options != null) {
            searchTerm = options.optString("filter");
            if (searchTerm.length() == 0) {
                searchTerm = "%";
            }
            else {
                searchTerm = "%" + searchTerm + "%";
            }

            try {
                multiple = options.getBoolean("multiple");
                if (!multiple) {
                    limit = 1;
                }
            } catch (JSONException e) {
                // Multiple was not specified so we assume the default is true.
            }

            accountType = options.optString("accountType", null);
            accountName = options.optString("accountName", null);
            allContacts = searchTerm == "%" && accountType == null && accountName == null;
        }
        else {
            searchTerm = "%";
            allContacts = true;
        }


        // Loop through the fields the user provided to see what data should be returned.
        HashMap<String, Boolean> populate = buildPopulationSet(options);

        // Build the ugly where clause and where arguments for one big query.
        WhereOptions whereOptions = buildWhereClause(fields, searchTerm, accountType, accountName);

        // Get all the id's where the search term matches the fields passed in.
        Cursor idCursor = mApp.getActivity().getContentResolver().query(RawContactsEntity.CONTENT_URI,
                new String[] { ContactsContract.RawContacts._ID },
                whereOptions.getWhere(),
                whereOptions.getWhereArgs(),
                ContactsContract.RawContacts._ID + " ASC");

        // Create a set of unique ids
        Set<String> contactIds = new HashSet<String>();
        int idColumn = -1;
        while (idCursor.moveToNext()) {
            if (idColumn < 0) {
                idColumn = idCursor.getColumnIndex(ContactsContract.RawContacts._ID);

            }
            contactIds.add(idCursor.getString(idColumn));
        }
        idCursor.close();

        Log.d(LOG_TAG, "contactIds.length: " + contactIds.size());

        // Build a query that only looks at ids
        WhereOptions idOptions = buildIdClause(contactIds, allContacts);

        // Determine which columns we should be fetching.
        HashSet<String> columnsToFetch = new HashSet<String>();
        columnsToFetch.add(ContactsContract.Data.CONTACT_ID);
        columnsToFetch.add(ContactsContract.RawContacts._ID);

        columnsToFetch.add(ContactsContract.Data.MIMETYPE);
        columnsToFetch.add(ContactsContract.RawContacts.VERSION);

        columnsToFetch.add(ContactsContract.RawContacts.DIRTY);
        columnsToFetch.add(ContactsContract.RawContacts.DELETED);
        columnsToFetch.add(ContactsContract.RawContacts.SOURCE_ID);
        columnsToFetch.add(ContactsContract.RawContacts.SYNC1);
        columnsToFetch.add(ContactsContract.RawContacts.SYNC2);
        columnsToFetch.add(ContactsContract.RawContacts.SYNC3);
        columnsToFetch.add(ContactsContract.RawContacts.SYNC4);



        if (isRequired("displayName", populate)) {
            columnsToFetch.add(ContactsContract.CommonDataKinds.StructuredName.DISPLAY_NAME);
        }
        if (isRequired("name", populate)) {
            columnsToFetch.add(ContactsContract.CommonDataKinds.StructuredName.FAMILY_NAME);
            columnsToFetch.add(ContactsContract.CommonDataKinds.StructuredName.GIVEN_NAME);
            columnsToFetch.add(ContactsContract.CommonDataKinds.StructuredName.MIDDLE_NAME);
            columnsToFetch.add(ContactsContract.CommonDataKinds.StructuredName.PREFIX);
            columnsToFetch.add(ContactsContract.CommonDataKinds.StructuredName.SUFFIX);
        }
        if (isRequired("phoneNumbers", populate)) {
            columnsToFetch.add(ContactsContract.CommonDataKinds.Phone._ID);
            columnsToFetch.add(ContactsContract.CommonDataKinds.Phone.NUMBER);
            columnsToFetch.add(ContactsContract.CommonDataKinds.Phone.TYPE);
        }
        if (isRequired("emails", populate)) {
            columnsToFetch.add(ContactsContract.CommonDataKinds.Email._ID);
            columnsToFetch.add(ContactsContract.CommonDataKinds.Email.DATA);
            columnsToFetch.add(ContactsContract.CommonDataKinds.Email.TYPE);
        }
        if (isRequired("addresses", populate)) {
            columnsToFetch.add(ContactsContract.CommonDataKinds.StructuredPostal._ID);
            columnsToFetch.add(ContactsContract.CommonDataKinds.Organization.TYPE);
            columnsToFetch.add(ContactsContract.CommonDataKinds.StructuredPostal.FORMATTED_ADDRESS);
            columnsToFetch.add(ContactsContract.CommonDataKinds.StructuredPostal.STREET);
            columnsToFetch.add(ContactsContract.CommonDataKinds.StructuredPostal.CITY);
            columnsToFetch.add(ContactsContract.CommonDataKinds.StructuredPostal.REGION);
            columnsToFetch.add(ContactsContract.CommonDataKinds.StructuredPostal.POSTCODE);
            columnsToFetch.add(ContactsContract.CommonDataKinds.StructuredPostal.COUNTRY);
        }
        if (isRequired("organizations", populate)) {
            columnsToFetch.add(ContactsContract.CommonDataKinds.Organization._ID);
            columnsToFetch.add(ContactsContract.CommonDataKinds.Organization.TYPE);
            columnsToFetch.add(ContactsContract.CommonDataKinds.Organization.DEPARTMENT);
            columnsToFetch.add(ContactsContract.CommonDataKinds.Organization.COMPANY);
            columnsToFetch.add(ContactsContract.CommonDataKinds.Organization.TITLE);
        }
        if (isRequired("ims", populate)) {
            columnsToFetch.add(ContactsContract.CommonDataKinds.Im._ID);
            columnsToFetch.add(ContactsContract.CommonDataKinds.Im.DATA);
            columnsToFetch.add(ContactsContract.CommonDataKinds.Im.TYPE);
        }
        if (isRequired("note", populate)) {
            columnsToFetch.add(ContactsContract.CommonDataKinds.Note.NOTE);
        }
        if (isRequired("nickname", populate)) {
            columnsToFetch.add(ContactsContract.CommonDataKinds.Nickname.NAME);
        }
        if (isRequired("urls", populate)) {
            columnsToFetch.add(ContactsContract.CommonDataKinds.Website._ID);
            columnsToFetch.add(ContactsContract.CommonDataKinds.Website.URL);
            columnsToFetch.add(ContactsContract.CommonDataKinds.Website.TYPE);
        }
        if (isRequired("birthday", populate) || isRequired("about", populate)) {
            columnsToFetch.add(ContactsContract.CommonDataKinds.Event.START_DATE);
            columnsToFetch.add(ContactsContract.CommonDataKinds.Event.TYPE);
            columnsToFetch.add(ContactsContract.CommonDataKinds.Event.LABEL);
        }
        if (isRequired("photos", populate)) {
            columnsToFetch.add(ContactsContract.CommonDataKinds.Photo._ID);
            columnsToFetch.add(ContactsContract.CommonDataKinds.Photo.PHOTO);
        }

        if (isRequired("relations", populate)) {
            // TODO : fetch it once for all kind of fields will be enought.
            columnsToFetch.add(BaseColumns._ID);
            columnsToFetch.add(ContactsContract.CommonDataKinds.Relation.NAME);
            columnsToFetch.add(ContactsContract.CommonDataKinds.Relation.TYPE);
            columnsToFetch.add(ContactsContract.CommonDataKinds.Relation.LABEL);
        }

        // Do the id query
        Cursor c = mApp.getActivity().getContentResolver().query(
            RawContactsEntity.CONTENT_URI,
                columnsToFetch.toArray(new String[] {}),
                idOptions.getWhere(),
                idOptions.getWhereArgs(),
                ContactsContract.RawContacts._ID + " ASC");

        JSONArray contacts = populateContactArray(limit, populate, c);
        return contacts;
    }

    /**
     * A special search that finds one contact by id
     *
     * @param id   contact to find by id (rawId)
     * @return     a JSONObject representing the contact
     * @throws JSONException
     */
    public JSONObject getContactById(String id) throws JSONException {
        // Call overloaded version with no desiredFields
        return getContactById(id, null);
    }

    @Override
    public JSONObject getContactById(String id, JSONArray desiredFields) throws JSONException {
        // Do the id query
        Cursor c = mApp.getActivity().getContentResolver().query(
                RawContactsEntity.CONTENT_URI,
                null,
                ContactsContract.RawContacts._ID + " = ? ",
                new String[] { id },
                ContactsContract.RawContacts.Data._ID + " ASC");

        HashMap<String, Boolean> populate = buildPopulationSet(
                new JSONObject().put("desiredFields", desiredFields)
                );

        JSONArray contacts = populateContactArray(1, populate, c);

        if (contacts.length() == 1) {
            return contacts.getJSONObject(0);
        } else {
            return null;
        }
    }

    /**
     * Creates an array of contacts from the cursor you pass in
     *
     * @param limit        max number of contacts for the array
     * @param populate     whether or not you should populate a certain value
     * @param c            the cursor
     * @return             a JSONArray of contacts
     */
    private JSONArray populateContactArray(int limit,
            HashMap<String, Boolean> populate, Cursor c) {

        String contactId = "";
        String rawId = "";
        String oldContactId = "";
        boolean newContact = true;
        String mimetype = "";
        int version = 0;
        boolean dirty = false;
        String sourceId = "";
        boolean deleted = false;
        String sync1 = "";
        String sync2 = "";
        String sync3 = "";
        String sync4 = "";

        JSONArray contacts = new JSONArray();
        JSONObject contact = new JSONObject();
        JSONArray organizations = new JSONArray();
        JSONArray addresses = new JSONArray();
        JSONArray phones = new JSONArray();
        JSONArray emails = new JSONArray();
        JSONArray ims = new JSONArray();
        JSONArray websites = new JSONArray();
        JSONArray photos = new JSONArray();
        JSONArray about = new JSONArray();
        JSONArray relations = new JSONArray();


        // Column indices
        int colContactId = c.getColumnIndex(ContactsContract.Data.CONTACT_ID);
        int colRawContactId = c.getColumnIndex(ContactsContract.RawContacts._ID);
        int colMimetype = c.getColumnIndex(ContactsContract.Data.MIMETYPE);
        int colDisplayName = c.getColumnIndex(ContactsContract.CommonDataKinds.StructuredName.DISPLAY_NAME);
        int colNote = c.getColumnIndex(ContactsContract.CommonDataKinds.Note.NOTE);
        int colNickname = c.getColumnIndex(ContactsContract.CommonDataKinds.Nickname.NAME);
        int colBirthday = c.getColumnIndex(ContactsContract.CommonDataKinds.Event.START_DATE);
        int colEventType = c.getColumnIndex(ContactsContract.CommonDataKinds.Event.TYPE);
        int colVersion = c.getColumnIndex(ContactsContract.RawContacts.VERSION);
        int colDirty = c.getColumnIndex(ContactsContract.RawContacts.DIRTY);
        int colSourceId = c.getColumnIndex(ContactsContract.RawContacts.SOURCE_ID);
        int colDeleted = c.getColumnIndex(ContactsContract.RawContacts.DELETED);
        int colSync1 = c.getColumnIndex(ContactsContract.RawContacts.SYNC1);
        int colSync2 = c.getColumnIndex(ContactsContract.RawContacts.SYNC2);
        int colSync3 = c.getColumnIndex(ContactsContract.RawContacts.SYNC3);
        int colSync4 = c.getColumnIndex(ContactsContract.RawContacts.SYNC4);


        if (c.getCount() > 0) {
            while (c.moveToNext() && (contacts.length() <= (limit - 1))) {
                try {
                    contactId = c.getString(colContactId); // may be null (if contact has been dissociated.)
                    rawId = c.getString(colRawContactId);
                    version = c.getInt(colVersion);
                    dirty = c.getInt(colDirty) == 1;
                    sourceId = c.getString(colSourceId);
                    deleted = c.getInt(colDeleted) == 1;
                    sync1 = c.getString(colSync1);
                    sync2 = c.getString(colSync2);
                    sync3 = c.getString(colSync3);
                    sync4 = c.getString(colSync4);

                    // If we are in the first row set the oldContactId
                    if (c.getPosition() == 0) {
                        oldContactId = rawId;
                    }

                    // When the contact ID changes we need to push the Contact object
                    // to the array of contacts and create new objects.
                    if (!oldContactId.equals(rawId)) {
                        // Populate the Contact object with it's arrays
                        // and push the contact into the contacts array
                        contacts.put(populateContact(contact, organizations, addresses, phones,
                                emails, ims, websites, photos, about, relations));


                        // Clean up the objects
                        contact = new JSONObject();
                        organizations = new JSONArray();
                        addresses = new JSONArray();
                        phones = new JSONArray();
                        emails = new JSONArray();
                        ims = new JSONArray();
                        websites = new JSONArray();
                        photos = new JSONArray();
                        about = new JSONArray();
                        relations = new JSONArray();

                        // Set newContact to true as we are starting to populate a new contact
                        newContact = true;
                    }

                    // When we detect a new contact set the ID and display name.
                    // These fields are available in every row in the result set returned.
                    if (newContact) {
                        newContact = false;
                        contact.put("id", contactId);
                        contact.put("rawId", rawId);
                        contact.put("version", version);
                        contact.put("dirty", dirty);
                        contact.put("sourceId", sourceId);
                        contact.put("deleted", deleted);
                        contact.put("sync1", sync1);
                        contact.put("sync2", sync2);
                        contact.put("sync3", sync3);
                        contact.put("sync4", sync4);
                    }

                    // Grab the mimetype of the current row as it will be used in a lot of comparisons
                    mimetype = c.getString(colMimetype);

                    // Defensive, mimetype might be null !
                    if (mimetype == null) {
                        mimetype = "";
                    }

                    if (mimetype.equals(ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE) && isRequired("name", populate)) {
                        contact.put("displayName", c.getString(colDisplayName));
                    }

                    if (mimetype.equals(ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE)
                            && isRequired("name", populate)) {
                        contact.put("name", nameQuery(c));
                    }
                    else if (mimetype.equals(ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE)
                            && isRequired("phoneNumbers", populate)) {
                        phones.put(phoneQuery(c));
                    }
                    else if (mimetype.equals(ContactsContract.CommonDataKinds.Email.CONTENT_ITEM_TYPE)
                            && isRequired("emails", populate)) {
                        emails.put(emailQuery(c));
                    }
                    else if (mimetype.equals(ContactsContract.CommonDataKinds.StructuredPostal.CONTENT_ITEM_TYPE)
                            && isRequired("addresses", populate)) {
                        addresses.put(addressQuery(c));
                    }
                    else if (mimetype.equals(ContactsContract.CommonDataKinds.Organization.CONTENT_ITEM_TYPE)
                            && isRequired("organizations", populate)) {
                        organizations.put(organizationQuery(c));
                    }
                    else if (mimetype.equals(ContactsContract.CommonDataKinds.Im.CONTENT_ITEM_TYPE)
                            && isRequired("ims", populate)) {
                        ims.put(imQuery(c));
                    }
                    else if (mimetype.equals(ContactsContract.CommonDataKinds.Note.CONTENT_ITEM_TYPE)
                            && isRequired("note", populate)) {
                        contact.put("note", c.getString(colNote));
                    }
                    else if (mimetype.equals(ContactsContract.CommonDataKinds.Nickname.CONTENT_ITEM_TYPE)
                            && isRequired("nickname", populate)) {
                        contact.put("nickname", c.getString(colNickname));
                    }
                    else if (mimetype.equals(ContactsContract.CommonDataKinds.Website.CONTENT_ITEM_TYPE)
                            && isRequired("urls", populate)) {
                        websites.put(websiteQuery(c));
                    }
                    else if (mimetype.equals(ContactsContract.CommonDataKinds.Event.CONTENT_ITEM_TYPE)) {
                        if (isRequired("birthday", populate) &&
                            ContactsContract.CommonDataKinds.Event.TYPE_BIRTHDAY == c.getInt(colEventType) &&
                            !contact.has("birthday")) {
                            contact.put("birthday", c.getString(colBirthday));
                        } else if (isRequired("about", populate)) {
                            about.put(contentQuery(c, EVENT_TYPES, EVENT_FIELDS));
                        }
                    }
                    else if (mimetype.equals(ContactsContract.CommonDataKinds.Photo.CONTENT_ITEM_TYPE)
                            && isRequired("photos", populate)) {
                        JSONObject photo = photoQuery(c, rawId);
                        if (photo != null) {
                            photos.put(photo);
                        }
                    }
                    else if (mimetype.equals(ContactsContract.CommonDataKinds.Relation.CONTENT_ITEM_TYPE)
                        && isRequired("relations", populate)) {
                        relations.put(contentQuery(c, RELATION_TYPES,
                            RELATION_FIELDS));
                    }

                } catch (JSONException e) {
                    Log.e(LOG_TAG, e.getMessage(), e);
                }

                // Set the old contact ID
                oldContactId = rawId;

            }

            // Push the last contact into the contacts array
            if (contacts.length() < limit) {
                contacts.put(populateContact(contact, organizations, addresses, phones,
                        emails, ims, websites, photos, relations, about));
            }
        }
        c.close();
        return contacts;
    }

    /**
     * Builds a where clause all all the ids passed into the method
     * @param contactIds a set of unique contact ids
     * @param allContacts means all rawcontact in android.
     * @return an object containing the selection and selection args
     */
    private WhereOptions buildIdClause(Set<String> contactIds, boolean allContacts) {
        WhereOptions options = new WhereOptions();


        // If the user is searching for every contact then short circuit the method
        // and return a shorter where clause to be searched.
        if (allContacts) {
            options.setWhere("(" + ContactsContract.RawContacts._ID + " LIKE ? )");
            options.setWhereArgs(new String[] { "%" });
            return options;
        }

        // This clause means that there are specific ID's to be populated
        Iterator<String> it = contactIds.iterator();
        StringBuffer buffer = new StringBuffer("(");

        while (it.hasNext()) {
            buffer.append("'" + it.next() + "'");
            if (it.hasNext()) {
                buffer.append(",");
            }
        }
        buffer.append(")");

        options.setWhere(ContactsContract.RawContacts._ID + " IN " + buffer.toString());
        options.setWhereArgs(null);

        return options;
    }

    /**
     * Create a new contact using a JSONObject to hold all the data.
     * @param contact
     * @param organizations array of organizations
     * @param addresses array of addresses
     * @param phones array of phones
     * @param emails array of emails
     * @param ims array of instant messenger addresses
     * @param websites array of websites
     * @param photos
     * @return
     */
    private JSONObject populateContact(JSONObject contact, JSONArray organizations,
            JSONArray addresses, JSONArray phones, JSONArray emails,
            JSONArray ims, JSONArray websites, JSONArray photos,
            JSONArray relations, JSONArray about) {
        try {
            // Only return the array if it has at least one entry
            if (organizations.length() > 0) {
                contact.put("organizations", organizations);
            }
            if (addresses.length() > 0) {
                contact.put("addresses", addresses);
            }
            if (phones.length() > 0) {
                contact.put("phoneNumbers", phones);
            }
            if (emails.length() > 0) {
                contact.put("emails", emails);
            }
            if (ims.length() > 0) {
                contact.put("ims", ims);
            }
            if (websites.length() > 0) {
                contact.put("urls", websites);
            }
            if (photos.length() > 0) {
                contact.put("photos", photos);
            }
            if (relations.length() > 0) {
                contact.put("relations", relations);
            }
            if (about.length() > 0) {
                contact.put("about", about);
            }
        } catch (JSONException e) {
            Log.e(LOG_TAG, e.getMessage(), e);
        }
        return contact;
    }

  /**
   * Take the search criteria passed into the method and create a SQL WHERE clause.
   * @param fields the properties to search against
   * @param searchTerm the string to search for
   * @return an object containing the selection and selection args
   */
  private WhereOptions buildWhereClause(JSONArray fields, String searchTerm, String accountType, String accountName) {

    ArrayList<String> where = new ArrayList<String>();
    ArrayList<String> whereArgs = new ArrayList<String>();

    WhereOptions options = new WhereOptions();

        /*
         * Special case where the user wants all fields returned
         */
        if ("%".equals(searchTerm)) {
            // Dummy to take every fields.
            where.add("(" + RawContacts.DATA_SET + " LIKE ? )");
            whereArgs.add(searchTerm);

        } else if (isWildCardSearch(fields)) {
            // Get all contacts that match the filter but return all properties
            where.add("(" + dbMap.get("displayName") + " LIKE ? )");
            whereArgs.add(searchTerm);
            where.add("(" + dbMap.get("name") + " LIKE ? AND "
                    + ContactsContract.Data.MIMETYPE + " = ? )");
            whereArgs.add(searchTerm);
            whereArgs.add(ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE);
            where.add("(" + dbMap.get("nickname") + " LIKE ? AND "
                    + ContactsContract.Data.MIMETYPE + " = ? )");
            whereArgs.add(searchTerm);
            whereArgs.add(ContactsContract.CommonDataKinds.Nickname.CONTENT_ITEM_TYPE);
            where.add("(" + dbMap.get("phoneNumbers") + " LIKE ? AND "
                    + ContactsContract.Data.MIMETYPE + " = ? )");
            whereArgs.add(searchTerm);
            whereArgs.add(ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE);
            where.add("(" + dbMap.get("emails") + " LIKE ? AND "
                    + ContactsContract.Data.MIMETYPE + " = ? )");
            whereArgs.add(searchTerm);
            whereArgs.add(ContactsContract.CommonDataKinds.Email.CONTENT_ITEM_TYPE);
            where.add("(" + dbMap.get("addresses") + " LIKE ? AND "
                    + ContactsContract.Data.MIMETYPE + " = ? )");
            whereArgs.add(searchTerm);
            whereArgs.add(ContactsContract.CommonDataKinds.StructuredPostal.CONTENT_ITEM_TYPE);
            where.add("(" + dbMap.get("ims") + " LIKE ? AND "
                    + ContactsContract.Data.MIMETYPE + " = ? )");
            whereArgs.add(searchTerm);
            whereArgs.add(ContactsContract.CommonDataKinds.Im.CONTENT_ITEM_TYPE);
            where.add("(" + dbMap.get("organizations") + " LIKE ? AND "
                    + ContactsContract.Data.MIMETYPE + " = ? )");
            whereArgs.add(searchTerm);
            whereArgs.add(ContactsContract.CommonDataKinds.Organization.CONTENT_ITEM_TYPE);
            where.add("(" + dbMap.get("note") + " LIKE ? AND "
                    + ContactsContract.Data.MIMETYPE + " = ? )");
            whereArgs.add(searchTerm);
            whereArgs.add(ContactsContract.CommonDataKinds.Note.CONTENT_ITEM_TYPE);
            where.add("(" + dbMap.get("urls") + " LIKE ? AND "
                    + ContactsContract.Data.MIMETYPE + " = ? )");
            whereArgs.add(searchTerm);
            whereArgs.add(ContactsContract.CommonDataKinds.Website.CONTENT_ITEM_TYPE);
        } else {

            String key;
            try {
                for (int i = 0; i < fields.length(); i++) {
                    key = fields.getString(i);

                    if (key.equals("id")) {
                        where.add("(" + dbMap.get(key) + " = ? )");
                        whereArgs.add(searchTerm.substring(1, searchTerm.length() - 1));
                    }
                    else if (key.startsWith("displayName")) {
                        where.add("(" + dbMap.get(key) + " LIKE ? )");
                        whereArgs.add(searchTerm);
                    }
                    else if (key.startsWith("name")) {
                        where.add("(" + dbMap.get(key) + " LIKE ? AND "
                                + ContactsContract.Data.MIMETYPE + " = ? )");
                        whereArgs.add(searchTerm);
                        whereArgs.add(ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE);
                    }
                    else if (key.startsWith("nickname")) {
                        where.add("(" + dbMap.get(key) + " LIKE ? AND "
                                + ContactsContract.Data.MIMETYPE + " = ? )");
                        whereArgs.add(searchTerm);
                        whereArgs.add(ContactsContract.CommonDataKinds.Nickname.CONTENT_ITEM_TYPE);
                    }
                    else if (key.startsWith("phoneNumbers")) {
                        where.add("(" + dbMap.get(key) + " LIKE ? AND "
                                + ContactsContract.Data.MIMETYPE + " = ? )");
                        whereArgs.add(searchTerm);
                        whereArgs.add(ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE);
                    }
                    else if (key.startsWith("emails")) {
                        where.add("(" + dbMap.get(key) + " LIKE ? AND "
                                + ContactsContract.Data.MIMETYPE + " = ? )");
                        whereArgs.add(searchTerm);
                        whereArgs.add(ContactsContract.CommonDataKinds.Email.CONTENT_ITEM_TYPE);
                    }
                    else if (key.startsWith("addresses")) {
                        where.add("(" + dbMap.get(key) + " LIKE ? AND "
                                + ContactsContract.Data.MIMETYPE + " = ? )");
                        whereArgs.add(searchTerm);
                        whereArgs.add(ContactsContract.CommonDataKinds.StructuredPostal.CONTENT_ITEM_TYPE);
                    }
                    else if (key.startsWith("ims")) {
                        where.add("(" + dbMap.get(key) + " LIKE ? AND "
                                + ContactsContract.Data.MIMETYPE + " = ? )");
                        whereArgs.add(searchTerm);
                        whereArgs.add(ContactsContract.CommonDataKinds.Im.CONTENT_ITEM_TYPE);
                    }
                    else if (key.startsWith("organizations")) {
                        where.add("(" + dbMap.get(key) + " LIKE ? AND "
                                + ContactsContract.Data.MIMETYPE + " = ? )");
                        whereArgs.add(searchTerm);
                        whereArgs.add(ContactsContract.CommonDataKinds.Organization.CONTENT_ITEM_TYPE);
                    }
                    //        else if (key.startsWith("birthday")) {
    //          where.add("(" + dbMap.get(key) + " LIKE ? AND "
    //              + ContactsContract.Data.MIMETYPE + " = ? )");
    //        }
                    else if (key.startsWith("note")) {
                        where.add("(" + dbMap.get(key) + " LIKE ? AND "
                                + ContactsContract.Data.MIMETYPE + " = ? )");
                        whereArgs.add(searchTerm);
                        whereArgs.add(ContactsContract.CommonDataKinds.Note.CONTENT_ITEM_TYPE);
                    }
                    else if (key.startsWith("urls")) {
                        where.add("(" + dbMap.get(key) + " LIKE ? AND "
                                + ContactsContract.Data.MIMETYPE + " = ? )");
                        whereArgs.add(searchTerm);
                        whereArgs.add(ContactsContract.CommonDataKinds.Website.CONTENT_ITEM_TYPE);
                    }

                    else if (key.startsWith("sourceId")) {
                        where.add("(" + ContactsContract.RawContacts.SOURCE_ID + " LIKE ? )");
                        whereArgs.add(searchTerm);
                    }
                    else if (key.startsWith("dirty")) {
                        where.add("(" + dbMap.get(key) + " LIKE ? )");
                        whereArgs.add(searchTerm);
                    }
                    else if (key.startsWith("deleted")) {
                        where.add("(" + dbMap.get(key) + " LIKE ? )");
                        whereArgs.add(searchTerm);
                    }
                    else if (key.startsWith("sync1")) {
                        where.add("(" + ContactsContract.RawContacts.SYNC1 + " LIKE ? )");
                        whereArgs.add(searchTerm);
                    }
                    else if (key.startsWith("sync2")) {
                        where.add("(" + ContactsContract.RawContacts.SYNC2 + " LIKE ? )");
                        whereArgs.add(searchTerm);
                    }
                    else if (key.startsWith("sync3")) {
                        where.add("(" + ContactsContract.RawContacts.SYNC3 + " LIKE ? )");
                        whereArgs.add(searchTerm);
                    }
                    else if (key.startsWith("sync4")) {
                        where.add("(" + ContactsContract.RawContacts.SYNC4 + " LIKE ? )");
                        whereArgs.add(searchTerm);
                    }
                    //TODO : handle about fields, and relations fields.
                }
            } catch (JSONException e) {
                Log.e(LOG_TAG, e.getMessage(), e);
            }
        }

        // Creating the where string
        StringBuffer selection = new StringBuffer();
        for (int i = 0; i < where.size(); i++) {
            selection.append(where.get(i));
            if (i != (where.size() - 1)) {
                selection.append(" OR ");
            }
        }

        if (accountType != null && accountName != null) {
            selection.insert(0, '(');
            selection.append(") AND (");

            // TODO: hack for accounts only filters.
            if ("%".equals(searchTerm)) {
                selection = new StringBuffer();
                selection.append("(");
                whereArgs.clear();
            }
            selection.append(RawContacts.ACCOUNT_TYPE);
            selection.append(" == ? )");
            selection.append(" AND (");
            selection.append(RawContacts.ACCOUNT_NAME);
            selection.append(" == ? )");

            whereArgs.add(accountType);
            whereArgs.add(accountName);
        }

        options.setWhere(selection.toString());

        // Creating the where args array
        String[] selectionArgs = new String[whereArgs.size()];
        for (int i = 0; i < whereArgs.size(); i++) {
            selectionArgs[i] = whereArgs.get(i);
        }
        options.setWhereArgs(selectionArgs);


        return options;
    }

    /**
     * If the user passes in the '*' wildcard character for search then they want all fields for each contact
     *
     * @param fields
     * @return true if wildcard search requested, false otherwise
     */
    private boolean isWildCardSearch(JSONArray fields) {
        // Only do a wildcard search if we are passed ["*"]
        if (fields.length() == 1) {
            try {
                if ("*".equals(fields.getString(0))) {
                    return true;
                }
            } catch (JSONException e) {
                return false;
            }
        }
        return false;
    }

    /**
    * Create a ContactOrganization JSONObject
    * @param cursor the current database row
    * @return a JSONObject representing a ContactOrganization
    */
    private JSONObject organizationQuery(Cursor cursor) {
        JSONObject organization = new JSONObject();
        try {
            organization.put("id", cursor.getString(cursor.getColumnIndex(ContactsContract.CommonDataKinds.Organization._ID)));
            organization.put("pref", false); // Android does not store pref attribute
            organization.put("type", getOrgType(cursor.getInt(cursor.getColumnIndex(ContactsContract.CommonDataKinds.Organization.TYPE))));
            organization.put("department", cursor.getString(cursor.getColumnIndex(ContactsContract.CommonDataKinds.Organization.DEPARTMENT)));
            organization.put("name", cursor.getString(cursor.getColumnIndex(ContactsContract.CommonDataKinds.Organization.COMPANY)));
            organization.put("title", cursor.getString(cursor.getColumnIndex(ContactsContract.CommonDataKinds.Organization.TITLE)));
        } catch (JSONException e) {
            Log.e(LOG_TAG, e.getMessage(), e);
        }
        return organization;
    }

    /**
     * Create a ContactAddress JSONObject
     * @param cursor the current database row
     * @return a JSONObject representing a ContactAddress
     */
    private JSONObject addressQuery(Cursor cursor) {
        JSONObject address = new JSONObject();
        try {
            address.put("id", cursor.getString(cursor.getColumnIndex(ContactsContract.CommonDataKinds.StructuredPostal._ID)));
            address.put("pref", false); // Android does not store pref attribute
            address.put("type", getAddressType(cursor.getInt(cursor.getColumnIndex(ContactsContract.CommonDataKinds.Organization.TYPE))));
            address.put("formatted", cursor.getString(cursor.getColumnIndex(ContactsContract.CommonDataKinds.StructuredPostal.FORMATTED_ADDRESS)));
            address.put("streetAddress", cursor.getString(cursor.getColumnIndex(ContactsContract.CommonDataKinds.StructuredPostal.STREET)));
            address.put("locality", cursor.getString(cursor.getColumnIndex(ContactsContract.CommonDataKinds.StructuredPostal.CITY)));
            address.put("region", cursor.getString(cursor.getColumnIndex(ContactsContract.CommonDataKinds.StructuredPostal.REGION)));
            address.put("postalCode", cursor.getString(cursor.getColumnIndex(ContactsContract.CommonDataKinds.StructuredPostal.POSTCODE)));
            address.put("country", cursor.getString(cursor.getColumnIndex(ContactsContract.CommonDataKinds.StructuredPostal.COUNTRY)));
        } catch (JSONException e) {
            Log.e(LOG_TAG, e.getMessage(), e);
        }
        return address;
    }

    /**
     * Create a ContactName JSONObject
     * @param cursor the current database row
     * @return a JSONObject representing a ContactName
     */
    private JSONObject nameQuery(Cursor cursor) {
        JSONObject contactName = new JSONObject();
        try {
            String familyName = cursor.getString(cursor.getColumnIndex(ContactsContract.CommonDataKinds.StructuredName.FAMILY_NAME));
            String givenName = cursor.getString(cursor.getColumnIndex(ContactsContract.CommonDataKinds.StructuredName.GIVEN_NAME));
            String middleName = cursor.getString(cursor.getColumnIndex(ContactsContract.CommonDataKinds.StructuredName.MIDDLE_NAME));
            String honorificPrefix = cursor.getString(cursor.getColumnIndex(ContactsContract.CommonDataKinds.StructuredName.PREFIX));
            String honorificSuffix = cursor.getString(cursor.getColumnIndex(ContactsContract.CommonDataKinds.StructuredName.SUFFIX));

            // Create the formatted name
            StringBuffer formatted = new StringBuffer("");
            if (honorificPrefix != null) {
                formatted.append(honorificPrefix + " ");
            }
            if (givenName != null) {
                formatted.append(givenName + " ");
            }
            if (middleName != null) {
                formatted.append(middleName + " ");
            }
            if (familyName != null) {
                formatted.append(familyName);
            }
            if (honorificSuffix != null) {
                formatted.append(" " + honorificSuffix);
            }

            contactName.put("familyName", familyName);
            contactName.put("givenName", givenName);
            contactName.put("middleName", middleName);
            contactName.put("honorificPrefix", honorificPrefix);
            contactName.put("honorificSuffix", honorificSuffix);
            contactName.put("formatted", formatted);
        } catch (JSONException e) {
            Log.e(LOG_TAG, e.getMessage(), e);
        }
        return contactName;
    }

    /**
     * Create a ContactField JSONObject
     * @param cursor the current database row
     * @return a JSONObject representing a ContactField
     */
    private JSONObject phoneQuery(Cursor cursor) {
        JSONObject phoneNumber = new JSONObject();
        try {
            phoneNumber.put("id", cursor.getString(cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone._ID)));
            phoneNumber.put("pref", false); // Android does not store pref attribute
            phoneNumber.put("value", cursor.getString(cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)));
            phoneNumber.put("type", getPhoneType(cursor.getInt(cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.TYPE))));
        } catch (JSONException e) {
            Log.e(LOG_TAG, e.getMessage(), e);
        } catch (Exception excp) {
            Log.e(LOG_TAG, excp.getMessage(), excp);
        }
        return phoneNumber;
    }

    /**
     * Create a ContactField JSONObject
     * @param cursor the current database row
     * @return a JSONObject representing a ContactField
     */
    private JSONObject emailQuery(Cursor cursor) {
        JSONObject email = new JSONObject();
        try {
            email.put("id", cursor.getString(cursor.getColumnIndex(ContactsContract.CommonDataKinds.Email._ID)));
            email.put("pref", false); // Android does not store pref attribute
            email.put("value", cursor.getString(cursor.getColumnIndex(ContactsContract.CommonDataKinds.Email.DATA)));
            email.put("type", getContactType(cursor.getInt(cursor.getColumnIndex(ContactsContract.CommonDataKinds.Email.TYPE))));
        } catch (JSONException e) {
            Log.e(LOG_TAG, e.getMessage(), e);
        }
        return email;
    }

    /**
     * Create a ContactField JSONObject
     * @param cursor the current database row
     * @return a JSONObject representing a ContactField
     */
    private JSONObject contentQuery(Cursor cursor,
        SparseArray<String> typesMap, String[] fieldNames) {
        JSONObject item = new JSONObject();
        try {
            item.put("id", cursor.getString(cursor.getColumnIndex(BaseColumns._ID)));
            item.put("pref", false); // Android does not store pref attribute
            item.put("value", cursor.getString(
                cursor.getColumnIndex(fieldNames[0])));

            String type = getType(typesMap, cursor.getInt(
                cursor.getColumnIndex(fieldNames[1])));

            if ("custom".equals(type)) {
                type = cursor.getString(cursor.getColumnIndex(fieldNames[2]));
            }
            item.put("type", type);

        } catch (JSONException e) {
            Log.e(LOG_TAG, e.getMessage(), e);
        }
        return item;
    }

    /**
     * Create a ContactField JSONObject
     * @param cursor the current database row
     * @return a JSONObject representing a ContactField
     */
    private JSONObject imQuery(Cursor cursor) {
        JSONObject im = new JSONObject();
        try {
            im.put("id", cursor.getString(cursor.getColumnIndex(ContactsContract.CommonDataKinds.Im._ID)));
            im.put("pref", false); // Android does not store pref attribute
            im.put("value", cursor.getString(cursor.getColumnIndex(ContactsContract.CommonDataKinds.Im.DATA)));
            String type = getImType(cursor.getInt(cursor.getColumnIndex(ContactsContract.CommonDataKinds.Im.PROTOCOL)));
            if ("custom".equals(type)) {
                type = cursor.getString(cursor.getColumnIndex(ContactsContract.CommonDataKinds.Im.CUSTOM_PROTOCOL));
            }
            im.put("type", type);

        } catch (JSONException e) {
            Log.e(LOG_TAG, e.getMessage(), e);
        }
        return im;
    }

    /**
     * Create a ContactField JSONObject
     * @param cursor the current database row
     * @return a JSONObject representing a ContactField
     */
    private JSONObject websiteQuery(Cursor cursor) {
        JSONObject website = new JSONObject();
        try {
            website.put("id", cursor.getString(cursor.getColumnIndex(ContactsContract.CommonDataKinds.Website._ID)));
            website.put("pref", false); // Android does not store pref attribute
            website.put("value", cursor.getString(cursor.getColumnIndex(ContactsContract.CommonDataKinds.Website.URL)));
            website.put("type", getContactType(cursor.getInt(cursor.getColumnIndex(ContactsContract.CommonDataKinds.Website.TYPE))));
        } catch (JSONException e) {
            Log.e(LOG_TAG, e.getMessage(), e);
        }
        return website;
    }

    /**
     * Create a ContactField JSONObject
     * @param contactId
     * @return a JSONObgject representing a ContactField
     */
    private JSONObject photoQuery(Cursor cursor, String rawContactId) {
        JSONObject photo = new JSONObject();
        try {
            photo.put("id", cursor.getString(cursor.getColumnIndex(ContactsContract.CommonDataKinds.Photo._ID)));
            photo.put("pref", false);
            photo.put("type", "base64");

            byte[] photoBlob = cursor.getBlob(cursor.getColumnIndex(
                    ContactsContract.CommonDataKinds.Photo.PHOTO));
            if (photoBlob == null) {
                return null;
            }

            photo.put("value", Base64.encodeToString(photoBlob, Base64.DEFAULT));

        } catch (JSONException e) {
            Log.e(LOG_TAG, e.getMessage(), e);
        }
        return photo;
    }

    @Override
    /**
     * This method will save a contact object into the devices contacts database.
     *
     * @param contact the contact to be saved.
     * @returns the id if the contact is successfully saved, null otherwise.
     */
    public String save(JSONObject contact) {
        return save(contact, null, null, false, false);
    }

    @Override
    /**
     * This method will save a contact object into the devices contacts database.
     *
     * @param contact the contact to be saved.
     * @param accountType the accountType to save the contact in.
     * @param accountName the accountName to save the contact in.
     * @param callerIsSyncAdapter should set the flag during requests ?
     * @return the id if the contact is successfully saved, null otherwise.
     */
    public String save(JSONObject contact, String accountType, String accountName,  boolean callerIsSyncAdapter, boolean resetFields) {
        if (accountType == null || accountName == null) {
            AccountManager mgr = AccountManager.get(mApp.getActivity());
            Account[] accounts = mgr.getAccounts();
            accountName = null;
            accountType = null;

            if (accounts.length == 1) {
                accountName = accounts[0].name;
                accountType = accounts[0].type;
            }
            else if (accounts.length > 1) {
                for (Account a : accounts) {
                    if (a.type.contains("eas") && a.name.matches(EMAIL_REGEXP)) /*Exchange ActiveSync*/{
                        accountName = a.name;
                        accountType = a.type;
                        break;
                    }
                }
                if (accountName == null) {
                    for (Account a : accounts) {
                        if (a.type.contains("com.google") && a.name.matches(EMAIL_REGEXP)) /*Google sync provider*/{
                            accountName = a.name;
                            accountType = a.type;
                            break;
                        }
                    }
                }
                if (accountName == null) {
                    for (Account a : accounts) {
                        if (a.name.matches(EMAIL_REGEXP)) /*Last resort, just look for an email address...*/{
                            accountName = a.name;
                            accountType = a.type;
                            break;
                        }
                    }
                }
            }
        }

        Log.d(LOG_TAG, "accountType: " + accountType + ", accountName: " + accountName);
        return saveContact(contact, accountType, accountName, callerIsSyncAdapter, resetFields);
        // String id = getJsonString(contact, "id");
        // if (id == null) {
        //     // Create new contact
        //     return createNewContact(contact, accountType, accountName, callerIsSyncAdapter);
        // } else {
        //     // Modify existing contact
        //     return modifyContact(id, contact, accountType, accountName, callerIsSyncAdapter, resetFields);
        // }
    }

    private String saveContact(JSONObject contact, String accountType, String accountName, boolean callerIsSyncAdapter, boolean resetFields) {
        // Get the RAW_CONTACT_ID which is needed to insert new values in an already existing contact.
        // But not needed to update existing values.
        int rawId = -1;
        try {
            rawId = Integer.parseInt(getJsonString(contact, "rawId"));
        } catch (NumberFormatException e) {
            // Should be a contact creation.
            rawId = -1;
        }


        // Create a list of attributes to add to the contact database
        ArrayList<ContentProviderOperation> ops = new ArrayList<ContentProviderOperation>();

        Uri contentUri = ContactsContract.Data.CONTENT_URI;

        // Android synchronisation tools
        if (callerIsSyncAdapter) {
            contentUri = contentUri.buildUpon()
                .appendQueryParameter(ContactsContract.CALLER_IS_SYNCADAPTER, "true")
                .build();
        }

        ContentValues syncValues = new ContentValues();

        String sourceId = getJsonString(contact, "sourceId");
        if (sourceId != null) {
            syncValues.put(RawContacts.SOURCE_ID, sourceId);
        }

        syncValues.put(RawContacts.DIRTY,
            contact.optBoolean("dirty") ? 1 : 0);
        syncValues.put(RawContacts.DELETED,
            contact.optBoolean("deleted") ? 1 : 0);

        String sync1 = getJsonString(contact, "sync1");
        if (sync1 != null) {
            syncValues.put(RawContacts.SYNC1, sync1);
        }

        String sync2 = getJsonString(contact, "sync2");
        if (sync2 != null) {
            syncValues.put(RawContacts.SYNC2, sync2);
        }

        String sync3 = getJsonString(contact, "sync3");
        if (sync3 != null) {
            syncValues.put(RawContacts.SYNC3, sync3);
        }

        String sync4 = getJsonString(contact, "sync4");
        if (sync4 != null) {
            syncValues.put(RawContacts.SYNC4, sync4);
        }

        syncValues.put(RawContacts.ACCOUNT_TYPE, accountType);
        syncValues.put(RawContacts.ACCOUNT_NAME, accountName);


        Uri rawContactUri = ContactsContract.RawContacts.CONTENT_URI;
        if (callerIsSyncAdapter) {
            contentUri = contentUri.buildUpon()
                .appendQueryParameter(ContactsContract.CALLER_IS_SYNCADAPTER, "true")
                .build();
        }

        ContentProviderOperation.Builder builder;

        if (rawId == -1) {
            builder = ContentProviderOperation.newInsert(rawContactUri);
        } else {
            builder = ContentProviderOperation.newUpdate(rawContactUri);
            builder.withSelection(ContactsContract.RawContacts._ID + "=?", new String[] { "" + rawId });
        }
        builder.withValues(syncValues);
        ops.add(builder.build());


        ContentValues nameValues = new ContentValues();

        JSONObject name;
        try {
            String displayName = getJsonString(contact, "displayName");
            name = contact.getJSONObject("name");

            if (displayName != null) {
                nameValues.put(StructuredName.DISPLAY_NAME, displayName);
            }

            String familyName = getJsonString(name, "familyName");
            if (familyName != null) {
                nameValues.put(StructuredName.FAMILY_NAME, familyName);
            }
            String middleName = getJsonString(name, "middleName");
            if (middleName != null) {
                nameValues.put(StructuredName.MIDDLE_NAME, middleName);
            }
            String givenName = getJsonString(name, "givenName");
            if (givenName != null) {
                nameValues.put(StructuredName.GIVEN_NAME, givenName);
            }
            String honorificPrefix = getJsonString(name, "honorificPrefix");
            if (honorificPrefix != null) {
                nameValues.put(StructuredName.PREFIX, honorificPrefix);
            }
            String honorificSuffix = getJsonString(name, "honorificSuffix");
            if (honorificSuffix != null) {
                nameValues.put(StructuredName.SUFFIX, honorificSuffix);
            }
        } catch (JSONException e1) {
            Log.d(LOG_TAG, "Could not get name");
        }

        builder = null;
        if (rawId == -1) {
            builder = ContentProviderOperation.newInsert(contentUri);
            builder.withValueBackReference(
                ContactsContract.Data.RAW_CONTACT_ID, 0);
            builder.withValue(
                ContactsContract.Data.MIMETYPE, StructuredName.CONTENT_ITEM_TYPE);
        } else {
            builder = ContentProviderOperation.newUpdate(contentUri)
                .withSelection(ContactsContract.Data.RAW_CONTACT_ID + "=? AND " +
                    ContactsContract.Data.MIMETYPE + "=?",
                    new String[] { "" + rawId, StructuredName.CONTENT_ITEM_TYPE });
        }

        builder.withValues(nameValues);
        ops.add(builder.build());



        try {
            // Modify note
            String note = getJsonString(contact, "note");
            addContactFieldOps(ops, contentUri, rawId, resetFields,
                note, Note.CONTENT_ITEM_TYPE, Note.NOTE);

             // Modify nickname
            String nickname = getJsonString(contact, "nickname");
            addContactFieldOps(ops, contentUri, rawId, resetFields,
                nickname, Nickname.CONTENT_ITEM_TYPE, Nickname.NAME);

        } catch (JSONException e) {
            Log.d(LOG_TAG, "JSONError while parsing.");
        }
        // Modify Photo
        JSONArray photos = null;
        try {
            photos = contact.getJSONArray("photos");
            addContactFieldOps(ops, contentUri, rawId, resetFields,
                photos, Phone.CONTENT_ITEM_TYPE, PHONE_TYPES, PHONE_FIELDS);
        } catch (JSONException e) {
            Log.d(LOG_TAG, "Could not get photos");
        }

        // Modify phone numbers
        JSONArray phones = null;
        try {
            phones = contact.getJSONArray("phoneNumbers");
            addContactFieldOps(ops, contentUri, rawId, resetFields,
                phones, Phone.CONTENT_ITEM_TYPE, PHONE_TYPES, PHONE_FIELDS);
        } catch (JSONException e) {
            Log.d(LOG_TAG, "Could not get phones");
        }

        // Modify emails
        JSONArray emails = null;
        try {
            emails = contact.getJSONArray("emails");
            addContactFieldOps(ops, contentUri, rawId, resetFields,
                emails, Email.CONTENT_ITEM_TYPE, CONTACT_TYPES, EMAIL_FIELDS);
        } catch (JSONException e) {
            Log.d(LOG_TAG, "Could not get emails");
        }

        // Modify addresses
        JSONArray addresses = null;
        try {
            addresses = contact.getJSONArray("addresses");
            addContactFieldOps(ops, contentUri, rawId, resetFields,
                addresses, StructuredPostal.CONTENT_ITEM_TYPE,
                ADDRESS_TYPES, ADDRESS_FIELDS);
        } catch (JSONException e) {
            Log.d(LOG_TAG, "Could not get addresses");
        }

        // Modify organizations
        JSONArray organizations = null;
        try {
            organizations = contact.getJSONArray("organizations");
            addContactFieldOps(ops, contentUri, rawId, resetFields,
                organizations, Organization.CONTENT_ITEM_TYPE, ORG_TYPES, ORG_FIELDS);

        } catch (JSONException e) {
            Log.d(LOG_TAG, "Could not get organizations");
        }
        // Modify IMs
        JSONArray ims = null;
        try {
            ims = contact.getJSONArray("ims");
            addContactFieldOps(ops, contentUri, rawId, resetFields,
                ims, Im.CONTENT_ITEM_TYPE, IM_TYPES, IM_FIELDS);
        } catch (JSONException e) {
            Log.d(LOG_TAG, "Could not get ims");
        }

        // Modify urls
        JSONArray websites = null;
        try {
            websites = contact.getJSONArray("urls");
            addContactFieldOps(ops, contentUri, rawId, resetFields,
                websites, Website.CONTENT_ITEM_TYPE, CONTACT_TYPES, WEBSITE_FIELDS);
        } catch (JSONException e) {
            Log.d(LOG_TAG, "Could not get urls");
        }

        // Modify relations:
        JSONArray relations = null;
        try {
            relations = contact.getJSONArray("relations");
            addContactFieldOps(ops, contentUri, rawId, resetFields,
                relations, Relation.CONTENT_ITEM_TYPE, RELATION_TYPES, RELATION_FIELDS);
        } catch (JSONException e) {
            Log.d(LOG_TAG, "Could not get relations");
        }

        // Modify events:
        JSONArray events = null;
        try {
            events = contact.getJSONArray("about");
            String birthday = getJsonString(contact, "birthday");
            if (birthday != null) {
                JSONObject bday = new JSONObject();
                bday.put("type", "birthday");
                bday.put("value", birthday);
                JSONArray eventsWBday = new JSONArray();
                eventsWBday.put(bday);

                for (int i = 0; i < events.length(); i++) {
                    eventsWBday.put(events.get(i));
                }
                events = eventsWBday;
            }
            addContactFieldOps(ops, contentUri, rawId, resetFields,
            events, Event.CONTENT_ITEM_TYPE,
            EVENT_TYPES, EVENT_FIELDS);
        } catch (JSONException e) {
            Log.d(LOG_TAG, "Could not get about");
        }




        boolean retVal = true;
        ContentProviderResult[] cpResults = null;
        //Add contact
        try {
            cpResults = mApp.getActivity().getContentResolver().applyBatch(ContactsContract.AUTHORITY, ops);

        } catch (RemoteException e) {
            Log.e(LOG_TAG, e.getMessage(), e);
            Log.e(LOG_TAG, Log.getStackTraceString(e), e);
            retVal = false;
        } catch (OperationApplicationException e) {
            Log.e(LOG_TAG, e.getMessage(), e);
            Log.e(LOG_TAG, Log.getStackTraceString(e), e);
            retVal = false;
        }

        String res = null;
        if (rawId == -1) {
            if (cpResults != null && cpResults.length >= 0) {
                res = cpResults[0].uri.getLastPathSegment();
            }
        } else if (retVal) {
            res = String.valueOf(rawId);
        }

        return res;

    }


    // /**
    //  * Creates a new contact and stores it in the database
    //  *
    //  * @param id the raw contact id which is required for linking items to the contact
    //  * @param contact the contact to be saved
    //  * @param account the account to be saved under
    //  */
    // private String modifyContact(String id, JSONObject contact, String accountType, String accountName, boolean callerIsSyncAdapter, boolean resetFields) {
    //     // Get the RAW_CONTACT_ID which is needed to insert new values in an already existing contact.
    //     // But not needed to update existing values.
    //     int rawId = (Integer.valueOf(getJsonString(contact, "rawId"))).intValue();

    //     Uri contentUri = ContactsContract.Data.CONTENT_URI;
    //     if (callerIsSyncAdapter) {
    //         contentUri = contentUri.buildUpon()
    //             .appendQueryParameter(ContactsContract.CALLER_IS_SYNCADAPTER, "true")
    //             .build();
    //     }

    //     // Create a list of attributes to add to the contact database
    //     ArrayList<ContentProviderOperation> ops = new ArrayList<ContentProviderOperation>();

    //     // Modify name
    //     JSONObject name;
    //     try {
    //         String displayName = getJsonString(contact, "displayName");
    //         name = contact.getJSONObject("name");
    //         if (displayName != null || name != null) {
    //             ContentProviderOperation.Builder builder = ContentProviderOperation.newUpdate(contentUri)
    //                     .withSelection(ContactsContract.Data.CONTACT_ID + "=? AND " +
    //                             ContactsContract.Data.MIMETYPE + "=?",
    //                             new String[] { id, ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE });

    //             if (displayName != null) {
    //                 builder.withValue(ContactsContract.CommonDataKinds.StructuredName.DISPLAY_NAME, displayName);
    //             }

    //             String familyName = getJsonString(name, "familyName");
    //             if (familyName != null) {
    //                 builder.withValue(ContactsContract.CommonDataKinds.StructuredName.FAMILY_NAME, familyName);
    //             }
    //             String middleName = getJsonString(name, "middleName");
    //             if (middleName != null) {
    //                 builder.withValue(ContactsContract.CommonDataKinds.StructuredName.MIDDLE_NAME, middleName);
    //             }
    //             String givenName = getJsonString(name, "givenName");
    //             if (givenName != null) {
    //                 builder.withValue(ContactsContract.CommonDataKinds.StructuredName.GIVEN_NAME, givenName);
    //             }
    //             String honorificPrefix = getJsonString(name, "honorificPrefix");
    //             if (honorificPrefix != null) {
    //                 builder.withValue(ContactsContract.CommonDataKinds.StructuredName.PREFIX, honorificPrefix);
    //             }
    //             String honorificSuffix = getJsonString(name, "honorificSuffix");
    //             if (honorificSuffix != null) {
    //                 builder.withValue(ContactsContract.CommonDataKinds.StructuredName.SUFFIX, honorificSuffix);
    //             }

    //             ops.add(builder.build());
    //         }
    //     } catch (JSONException e1) {
    //         Log.d(LOG_TAG, "Could not get name");
    //     }

    //     // Modify phone numbers
    //     JSONArray phones = null;
    //     try {
    //         phones = contact.getJSONArray("phoneNumbers");
    //         if (phones != null) {
    //             // Delete all the phones
    //             if (phones.length() == 0 || resetFields) {
    //                 ops.add(ContentProviderOperation.newDelete(contentUri)
    //                         .withSelection(ContactsContract.Data.RAW_CONTACT_ID + "=? AND " +
    //                                 ContactsContract.Data.MIMETYPE + "=?",
    //                                 new String[] { "" + rawId, ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE })
    //                         .build());
    //             }
    //             // Modify or add a phone
    //             for (int i = 0; i < phones.length(); i++) {
    //                 JSONObject phone = (JSONObject) phones.get(i);
    //                 String phoneId = getJsonString(phone, "id");
    //                 // This is a new phone so do a DB insert
    //                 if (phoneId == null || resetFields) {
    //                     ContentValues contentValues = new ContentValues();
    //                     contentValues.put(ContactsContract.Data.RAW_CONTACT_ID, rawId);
    //                     contentValues.put(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE);
    //                     contentValues.put(ContactsContract.CommonDataKinds.Phone.NUMBER, getJsonString(phone, "value"));
    //                     contentValues.put(ContactsContract.CommonDataKinds.Phone.TYPE, getPhoneType(getJsonString(phone, "type")));

    //                     ops.add(ContentProviderOperation.newInsert(
    //                             contentUri).withValues(contentValues).build());
    //                 }
    //                 // This is an existing phone so do a DB update
    //                 else {
    //                     ops.add(ContentProviderOperation.newUpdate(contentUri)
    //                             .withSelection(ContactsContract.CommonDataKinds.Phone._ID + "=? AND " +
    //                                     ContactsContract.Data.MIMETYPE + "=?",
    //                                     new String[] { phoneId, ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE })
    //                             .withValue(ContactsContract.CommonDataKinds.Phone.NUMBER, getJsonString(phone, "value"))
    //                             .withValue(ContactsContract.CommonDataKinds.Phone.TYPE, getPhoneType(getJsonString(phone, "type")))
    //                             .build());
    //                 }
    //             }
    //         }
    //     } catch (JSONException e) {
    //         Log.d(LOG_TAG, "Could not get phone numbers");
    //     }

    //     // Modify emails
    //     JSONArray emails = null;
    //     try {
    //         emails = contact.getJSONArray("emails");
    //         if (emails != null) {
    //             // Delete all the emails
    //             if (emails.length() == 0 || resetFields) {
    //                 ops.add(ContentProviderOperation.newDelete(contentUri)
    //                         .withSelection(ContactsContract.Data.RAW_CONTACT_ID + "=? AND " +
    //                                 ContactsContract.Data.MIMETYPE + "=?",
    //                                 new String[] { "" + rawId, ContactsContract.CommonDataKinds.Email.CONTENT_ITEM_TYPE })
    //                         .build());
    //             }
    //             // Modify or add a email
    //             for (int i = 0; i < emails.length(); i++) {
    //                 JSONObject email = (JSONObject) emails.get(i);
    //                 String emailId = getJsonString(email, "id");
    //                 // This is a new email so do a DB insert
    //                 if (emailId == null || resetFields) {
    //                     ContentValues contentValues = new ContentValues();
    //                     contentValues.put(ContactsContract.Data.RAW_CONTACT_ID, rawId);
    //                     contentValues.put(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.Email.CONTENT_ITEM_TYPE);
    //                     contentValues.put(ContactsContract.CommonDataKinds.Email.DATA, getJsonString(email, "value"));
    //                     contentValues.put(ContactsContract.CommonDataKinds.Email.TYPE, getContactType(getJsonString(email, "type")));

    //                     ops.add(ContentProviderOperation.newInsert(
    //                             contentUri).withValues(contentValues).build());
    //                 }
    //                 // This is an existing email so do a DB update
    //                 else {
    //                     String emailValue=getJsonString(email, "value");
    //                     if(!emailValue.isEmpty()) {
    //                         ops.add(ContentProviderOperation.newUpdate(contentUri)
    //                             .withSelection(ContactsContract.CommonDataKinds.Email._ID + "=? AND " +
    //                                     ContactsContract.Data.MIMETYPE + "=?",
    //                                     new String[] { emailId, ContactsContract.CommonDataKinds.Email.CONTENT_ITEM_TYPE })
    //                             .withValue(ContactsContract.CommonDataKinds.Email.DATA, getJsonString(email, "value"))
    //                             .withValue(ContactsContract.CommonDataKinds.Email.TYPE, getContactType(getJsonString(email, "type")))
    //                             .build());
    //                     } else {
    //                         ops.add(ContentProviderOperation.newDelete(contentUri)
    //                                 .withSelection(ContactsContract.CommonDataKinds.Email._ID + "=? AND " +
    //                                         ContactsContract.Data.MIMETYPE + "=?",
    //                                         new String[] { emailId, ContactsContract.CommonDataKinds.Email.CONTENT_ITEM_TYPE })
    //                                 .build());
    //                     }
    //                 }
    //             }
    //         }
    //     } catch (JSONException e) {
    //         Log.d(LOG_TAG, "Could not get emails");
    //     }

    //     // Modify addresses
    //     JSONArray addresses = null;
    //     try {
    //         addresses = contact.getJSONArray("addresses");
    //         if (addresses != null) {
    //             // Delete all the addresses
    //             if (addresses.length() == 0 || resetFields) {
    //                 ops.add(ContentProviderOperation.newDelete(contentUri)
    //                         .withSelection(ContactsContract.Data.RAW_CONTACT_ID + "=? AND " +
    //                                 ContactsContract.Data.MIMETYPE + "=?",
    //                                 new String[] { "" + rawId, ContactsContract.CommonDataKinds.StructuredPostal.CONTENT_ITEM_TYPE })
    //                         .build());
    //             }
    //             // Modify or add a address
    //             for (int i = 0; i < addresses.length(); i++) {
    //                 JSONObject address = (JSONObject) addresses.get(i);
    //                 String addressId = getJsonString(address, "id");
    //                 // This is a new address so do a DB insert
    //                 if (addressId == null || resetFields) {
    //                     ContentValues contentValues = new ContentValues();
    //                     contentValues.put(ContactsContract.Data.RAW_CONTACT_ID, rawId);
    //                     contentValues.put(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.StructuredPostal.CONTENT_ITEM_TYPE);
    //                     contentValues.put(ContactsContract.CommonDataKinds.StructuredPostal.TYPE, getAddressType(getJsonString(address, "type")));
    //                     contentValues.put(ContactsContract.CommonDataKinds.StructuredPostal.FORMATTED_ADDRESS, getJsonString(address, "formatted"));
    //                     contentValues.put(ContactsContract.CommonDataKinds.StructuredPostal.STREET, getJsonString(address, "streetAddress"));
    //                     contentValues.put(ContactsContract.CommonDataKinds.StructuredPostal.CITY, getJsonString(address, "locality"));
    //                     contentValues.put(ContactsContract.CommonDataKinds.StructuredPostal.REGION, getJsonString(address, "region"));
    //                     contentValues.put(ContactsContract.CommonDataKinds.StructuredPostal.POSTCODE, getJsonString(address, "postalCode"));
    //                     contentValues.put(ContactsContract.CommonDataKinds.StructuredPostal.COUNTRY, getJsonString(address, "country"));

    //                     ops.add(ContentProviderOperation.newInsert(
    //                             contentUri).withValues(contentValues).build());
    //                 }
    //                 // This is an existing address so do a DB update
    //                 else {
    //                     ops.add(ContentProviderOperation.newUpdate(contentUri)
    //                             .withSelection(ContactsContract.CommonDataKinds.StructuredPostal._ID + "=? AND " +
    //                                     ContactsContract.Data.MIMETYPE + "=?",
    //                                     new String[] { addressId, ContactsContract.CommonDataKinds.StructuredPostal.CONTENT_ITEM_TYPE })
    //                             .withValue(ContactsContract.CommonDataKinds.StructuredPostal.TYPE, getAddressType(getJsonString(address, "type")))
    //                             .withValue(ContactsContract.CommonDataKinds.StructuredPostal.FORMATTED_ADDRESS, getJsonString(address, "formatted"))
    //                             .withValue(ContactsContract.CommonDataKinds.StructuredPostal.STREET, getJsonString(address, "streetAddress"))
    //                             .withValue(ContactsContract.CommonDataKinds.StructuredPostal.CITY, getJsonString(address, "locality"))
    //                             .withValue(ContactsContract.CommonDataKinds.StructuredPostal.REGION, getJsonString(address, "region"))
    //                             .withValue(ContactsContract.CommonDataKinds.StructuredPostal.POSTCODE, getJsonString(address, "postalCode"))
    //                             .withValue(ContactsContract.CommonDataKinds.StructuredPostal.COUNTRY, getJsonString(address, "country"))
    //                             .build());
    //                 }
    //             }
    //         }
    //     } catch (JSONException e) {
    //         Log.d(LOG_TAG, "Could not get addressles");
    //     }

    //     // Modify organizations
    //     JSONArray organizations = null;
    //     try {
    //         organizations = contact.getJSONArray("organizations");
    //         if (organizations != null) {
    //             // Delete all the organizations
    //             if (organizations.length() == 0 || resetFields) {
    //                 ops.add(ContentProviderOperation.newDelete(contentUri)
    //                         .withSelection(ContactsContract.Data.RAW_CONTACT_ID + "=? AND " +
    //                                 ContactsContract.Data.MIMETYPE + "=?",
    //                                 new String[] { "" + rawId, ContactsContract.CommonDataKinds.Organization.CONTENT_ITEM_TYPE })
    //                         .build());
    //             }
    //             // Modify or add a organization
    //             for (int i = 0; i < organizations.length(); i++) {
    //                 JSONObject org = (JSONObject) organizations.get(i);
    //                 String orgId = getJsonString(org, "id");
    //                 // This is a new organization so do a DB insert
    //                 if (orgId == null || resetFields) {
    //                     ContentValues contentValues = new ContentValues();
    //                     contentValues.put(ContactsContract.Data.RAW_CONTACT_ID, rawId);
    //                     contentValues.put(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.Organization.CONTENT_ITEM_TYPE);
    //                     contentValues.put(ContactsContract.CommonDataKinds.Organization.TYPE, getOrgType(getJsonString(org, "type")));
    //                     contentValues.put(ContactsContract.CommonDataKinds.Organization.DEPARTMENT, getJsonString(org, "department"));
    //                     contentValues.put(ContactsContract.CommonDataKinds.Organization.COMPANY, getJsonString(org, "name"));
    //                     contentValues.put(ContactsContract.CommonDataKinds.Organization.TITLE, getJsonString(org, "title"));

    //                     ops.add(ContentProviderOperation.newInsert(
    //                             contentUri).withValues(contentValues).build());
    //                 }
    //                 // This is an existing organization so do a DB update
    //                 else {
    //                     ops.add(ContentProviderOperation.newUpdate(contentUri)
    //                             .withSelection(ContactsContract.CommonDataKinds.Organization._ID + "=? AND " +
    //                                     ContactsContract.Data.MIMETYPE + "=?",
    //                                     new String[] { orgId, ContactsContract.CommonDataKinds.Organization.CONTENT_ITEM_TYPE })
    //                             .withValue(ContactsContract.CommonDataKinds.Organization.TYPE, getOrgType(getJsonString(org, "type")))
    //                             .withValue(ContactsContract.CommonDataKinds.Organization.DEPARTMENT, getJsonString(org, "department"))
    //                             .withValue(ContactsContract.CommonDataKinds.Organization.COMPANY, getJsonString(org, "name"))
    //                             .withValue(ContactsContract.CommonDataKinds.Organization.TITLE, getJsonString(org, "title"))
    //                             .build());
    //                 }
    //             }
    //         }
    //     } catch (JSONException e) {
    //         Log.d(LOG_TAG, "Could not get organizations");
    //     }

    //     // Modify IMs
    //     JSONArray ims = null;
    //     try {
    //         ims = contact.getJSONArray("ims");
    //         if (ims != null) {
    //             // Delete all the ims
    //             if (ims.length() == 0 || resetFields) {
    //                 ops.add(ContentProviderOperation.newDelete(contentUri)
    //                         .withSelection(ContactsContract.Data.RAW_CONTACT_ID + "=? AND " +
    //                                 ContactsContract.Data.MIMETYPE + "=?",
    //                                 new String[] { "" + rawId, ContactsContract.CommonDataKinds.Im.CONTENT_ITEM_TYPE })
    //                         .build());
    //             }
    //             // Modify or add a im
    //             for (int i = 0; i < ims.length(); i++) {
    //                 JSONObject im = (JSONObject) ims.get(i);
    //                 ContentValues contentValues = new ContentValues();
    //                 contentValues.put(ContactsContract.CommonDataKinds.Im.DATA, getJsonString(im, "value"));

    //                 String imType = getJsonString(im, "type");
    //                 int imTypeCode = getImType(imType);
    //                 contentValues.put(ContactsContract.CommonDataKinds.Im.PROTOCOL, imTypeCode);

    //                 if (imTypeCode == ContactsContract.CommonDataKinds.Im.PROTOCOL_CUSTOM) {
    //                     contentValues.put(ContactsContract.CommonDataKinds.Im.CUSTOM_PROTOCOL, imType);
    //                 }

    //                 String imId = getJsonString(im, "id");
    //                 // This is a new IM so do a DB insert
    //                 if (imId == null || resetFields) {
    //                     contentValues.put(ContactsContract.Data.RAW_CONTACT_ID, rawId);
    //                     contentValues.put(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.Im.CONTENT_ITEM_TYPE);


    //                     ops.add(ContentProviderOperation.newInsert(
    //                             contentUri).withValues(contentValues).build());
    //                 }
    //                 // This is an existing IM so do a DB update
    //                 else {
    //                     ops.add(ContentProviderOperation.newUpdate(contentUri)
    //                             .withSelection(ContactsContract.CommonDataKinds.Im._ID + "=? AND " +
    //                                     ContactsContract.Data.MIMETYPE + "=?",
    //                                     new String[] { imId, ContactsContract.CommonDataKinds.Im.CONTENT_ITEM_TYPE })
    //                             .withValues(contentValues)
    //                             .build());
    //                 }
    //             }
    //         }
    //     } catch (JSONException e) {
    //         Log.d(LOG_TAG, "Could not get emails");
    //     }

    //     // Modify note
    //     String note = getJsonString(contact, "note");
    //     ops.add(ContentProviderOperation.newUpdate(contentUri)
    //             .withSelection(ContactsContract.Data.CONTACT_ID + "=? AND " +
    //                     ContactsContract.Data.MIMETYPE + "=?",
    //                     new String[] { id, ContactsContract.CommonDataKinds.Note.CONTENT_ITEM_TYPE })
    //             .withValue(ContactsContract.CommonDataKinds.Note.NOTE, note)
    //             .build());

    //     // Modify nickname
    //     String nickname = getJsonString(contact, "nickname");
    //     if (nickname != null) {
    //         ops.add(ContentProviderOperation.newUpdate(contentUri)
    //                 .withSelection(ContactsContract.Data.CONTACT_ID + "=? AND " +
    //                         ContactsContract.Data.MIMETYPE + "=?",
    //                         new String[] { id, ContactsContract.CommonDataKinds.Nickname.CONTENT_ITEM_TYPE })
    //                 .withValue(ContactsContract.CommonDataKinds.Nickname.NAME, nickname)
    //                 .build());
    //     }

    //     // Modify urls
    //     JSONArray websites = null;
    //     try {
    //         websites = contact.getJSONArray("urls");
    //         if (websites != null) {
    //             // Delete all the websites
    //             if (websites.length() == 0 || resetFields) {
    //                 Log.d(LOG_TAG, "This means we should be deleting all the phone numbers.");
    //                 ops.add(ContentProviderOperation.newDelete(contentUri)
    //                         .withSelection(ContactsContract.Data.RAW_CONTACT_ID + "=? AND " +
    //                                 ContactsContract.Data.MIMETYPE + "=?",
    //                                 new String[] { "" + rawId, ContactsContract.CommonDataKinds.Website.CONTENT_ITEM_TYPE })
    //                         .build());
    //             }
    //             // Modify or add a website
    //             for (int i = 0; i < websites.length(); i++) {
    //                 JSONObject website = (JSONObject) websites.get(i);
    //                 String websiteId = getJsonString(website, "id");
    //                 // This is a new website so do a DB insert
    //                 if (websiteId == null || resetFields) {
    //                     ContentValues contentValues = new ContentValues();
    //                     contentValues.put(ContactsContract.Data.RAW_CONTACT_ID, rawId);
    //                     contentValues.put(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.Website.CONTENT_ITEM_TYPE);
    //                     contentValues.put(ContactsContract.CommonDataKinds.Website.DATA, getJsonString(website, "value"));
    //                     contentValues.put(ContactsContract.CommonDataKinds.Website.TYPE, getContactType(getJsonString(website, "type")));

    //                     ops.add(ContentProviderOperation.newInsert(
    //                            contentUri).withValues(contentValues).build());
    //                 }
    //                 // This is an existing website so do a DB update
    //                 else {
    //                     ops.add(ContentProviderOperation.newUpdate(contentUri)
    //                             .withSelection(ContactsContract.CommonDataKinds.Website._ID + "=? AND " +
    //                                     ContactsContract.Data.MIMETYPE + "=?",
    //                                     new String[] { websiteId, ContactsContract.CommonDataKinds.Website.CONTENT_ITEM_TYPE })
    //                             .withValue(ContactsContract.CommonDataKinds.Website.DATA, getJsonString(website, "value"))
    //                             .withValue(ContactsContract.CommonDataKinds.Website.TYPE, getContactType(getJsonString(website, "type")))
    //                             .build());
    //                 }
    //             }
    //         }
    //     } catch (JSONException e) {
    //         Log.d(LOG_TAG, "Could not get websites");
    //     }

    //     // Modify birthday
    //     String birthday = getJsonString(contact, "birthday");
    //     if (birthday != null) {
    //         ops.add(ContentProviderOperation.newUpdate(contentUri)
    //                 .withSelection(ContactsContract.Data.CONTACT_ID + "=? AND " +
    //                         ContactsContract.Data.MIMETYPE + "=? AND " +
    //                         ContactsContract.CommonDataKinds.Event.TYPE + "=?",
    //                         new String[] { id, ContactsContract.CommonDataKinds.Event.CONTENT_ITEM_TYPE, new String("" + ContactsContract.CommonDataKinds.Event.TYPE_BIRTHDAY) })
    //                 .withValue(ContactsContract.CommonDataKinds.Event.TYPE, ContactsContract.CommonDataKinds.Event.TYPE_BIRTHDAY)
    //                 .withValue(ContactsContract.CommonDataKinds.Event.START_DATE, birthday)
    //                 .build());
    //     }

    //     // Modify photos
    //     JSONArray photos = null;
    //     try {
    //         photos = contact.getJSONArray("photos");
    //         if (photos != null) {
    //             // Delete all the photos
    //             if (photos.length() == 0 || resetFields) {
    //                 ops.add(ContentProviderOperation.newDelete(contentUri)
    //                         .withSelection(ContactsContract.Data.RAW_CONTACT_ID + "=? AND " +
    //                                 ContactsContract.Data.MIMETYPE + "=?",
    //                                 new String[] { "" + rawId, ContactsContract.CommonDataKinds.Photo.CONTENT_ITEM_TYPE })
    //                         .build());
    //             }
    //             // Modify or add a photo
    //             for (int i = 0; i < photos.length(); i++) {
    //                 JSONObject photo = (JSONObject) photos.get(i);
    //                 String photoId = getJsonString(photo, "id");
    //                 byte[] bytes = getPhotoBytes(photo);
    //                 // This is a new photo so do a DB insert
    //                 if (photoId == null || resetFields) {
    //                     ContentValues contentValues = new ContentValues();
    //                     contentValues.put(ContactsContract.Data.RAW_CONTACT_ID, rawId);
    //                     contentValues.put(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.Photo.CONTENT_ITEM_TYPE);
    //                     contentValues.put(ContactsContract.Data.IS_SUPER_PRIMARY, 1);
    //                     contentValues.put(ContactsContract.CommonDataKinds.Photo.PHOTO, bytes);

    //                     ops.add(ContentProviderOperation.newInsert(
    //                             contentUri).withValues(contentValues).build());
    //                 }
    //                 // This is an existing photo so do a DB update
    //                 else {
    //                     ops.add(ContentProviderOperation.newUpdate(contentUri)
    //                             .withSelection(ContactsContract.CommonDataKinds.Photo._ID + "=? AND " +
    //                                     ContactsContract.Data.MIMETYPE + "=?",
    //                                     new String[] { photoId, ContactsContract.CommonDataKinds.Photo.CONTENT_ITEM_TYPE })
    //                             .withValue(ContactsContract.Data.IS_SUPER_PRIMARY, 1)
    //                             .withValue(ContactsContract.CommonDataKinds.Photo.PHOTO, bytes)
    //                             .build());
    //                 }
    //             }
    //         }
    //     } catch (JSONException e) {
    //         Log.d(LOG_TAG, "Could not get photos");
    //     }

    //     // Modify relations:
    //     JSONArray relations = null;
    //     try {
    //         relations = contact.getJSONArray("relations");
    //         modifyContent(ops, contentUri, rawId, resetFields,
    //         relations,
    //         ContactsContract.CommonDataKinds.Relation.CONTENT_ITEM_TYPE,
    //         RELATION_TYPES,
    //         ContactsContract.CommonDataKinds.Relation.NAME,
    //         ContactsContract.CommonDataKinds.Relation.TYPE,
    //         ContactsContract.CommonDataKinds.Relation.LABEL);
    //     } catch (JSONException e) {
    //         Log.d(LOG_TAG, "Could not get relations");
    //     }

    //     // Modify events:
    //     JSONArray events = null;
    //     try {
    //         events = contact.getJSONArray("about");
    //         modifyContent(ops, contentUri, rawId, resetFields,
    //         events,
    //         ContactsContract.CommonDataKinds.Event.CONTENT_ITEM_TYPE,
    //         EVENT_TYPES,
    //         ContactsContract.CommonDataKinds.Event.START_DATE,
    //         ContactsContract.CommonDataKinds.Event.TYPE,
    //         ContactsContract.CommonDataKinds.Event.LABEL);
    //     } catch (JSONException e) {
    //         Log.d(LOG_TAG, "Could not get about");
    //     }


    //     // ModifySync :
    //     //Add contact type
    //     contentUri = ContactsContract.RawContacts.CONTENT_URI;
    //     if (callerIsSyncAdapter) {
    //         contentUri = contentUri.buildUpon()
    //             .appendQueryParameter(ContactsContract.CALLER_IS_SYNCADAPTER, "true")
    //             .build();
    //     }

    //     ContentProviderOperation.Builder builder = ContentProviderOperation.newUpdate(contentUri);
    //     builder.withSelection(ContactsContract.RawContacts._ID + "=?", new String[] { "" + rawId });


    //     String sourceId = getJsonString(contact, "sourceId");
    //     if (sourceId != null) {
    //             builder.withValue(ContactsContract.RawContacts.SOURCE_ID, sourceId);
    //     }

    //     int dirty = contact.optBoolean("dirty") ? 1 : 0 ;
    //     builder.withValue(ContactsContract.RawContacts.DIRTY, dirty);

    //     int deleted = contact.optBoolean("deleted") ? 1 : 0 ;
    //     builder.withValue(ContactsContract.RawContacts.DELETED, deleted);


    //     String sync1 = getJsonString(contact, "sync1");
    //     if (sync1 != null) {
    //         builder.withValue(ContactsContract.RawContacts.SYNC1, sync1);
    //     }

    //     String sync2 = getJsonString(contact, "sync2");
    //     if (sync2 != null) {
    //         builder.withValue(ContactsContract.RawContacts.SYNC2, sync2);
    //     }

    //     String sync3 = getJsonString(contact, "sync3");
    //     if (sync3 != null) {
    //         builder.withValue(ContactsContract.RawContacts.SYNC3, sync3);
    //     }

    //     String sync4 = getJsonString(contact, "sync4");
    //     if (sync4 != null) {
    //         builder.withValue(ContactsContract.RawContacts.SYNC4, sync4);
    //     }
    //     ops.add(builder.build());


    //     boolean retVal = true;

    //     //Modify contact
    //     try {
    //         mApp.getActivity().getContentResolver().applyBatch(ContactsContract.AUTHORITY, ops);
    //     } catch (RemoteException e) {
    //         Log.e(LOG_TAG, e.getMessage(), e);
    //         Log.e(LOG_TAG, Log.getStackTraceString(e), e);
    //         retVal = false;
    //     } catch (OperationApplicationException e) {
    //         Log.e(LOG_TAG, e.getMessage(), e);
    //         Log.e(LOG_TAG, Log.getStackTraceString(e), e);
    //         retVal = false;
    //     }

    //     // if the save was a success return the contact RAW_ID
    //     if (retVal) {
    //         return String.valueOf(rawId);
    //     } else {
    //         return null;
    //     }
    // }


    // private void modifyContent(ArrayList<ContentProviderOperation> ops, Uri contentUri, int rawId, boolean resetFields,
    //     JSONArray items, String contentItemType, SparseArray<String> typesMap,
    //     String valueFieldName, String typeFieldName, String labelFieldName) throws JSONException {

    //     if (items != null) {
    //         // Delete all the
    //         if (items.length() == 0 || resetFields) {
    //             Log.d(LOG_TAG, "This means we should be deleting all the items.");
    //             ops.add(ContentProviderOperation.newDelete(contentUri)
    //                     .withSelection(ContactsContract.Data.RAW_CONTACT_ID + "=? AND " +
    //                             ContactsContract.Data.MIMETYPE + "=?",
    //                             new String[] { "" + rawId, contentItemType })
    //                     .build());
    //         }
    //         // Modify or add a items
    //         for (int i = 0; i < items.length(); i++) {
    //             JSONObject item = (JSONObject) items.get(i);

    //             String itemId = getJsonString(item, "id");
    //             ContentValues contentValues = buildContentValues(item, typesMap,
    //                 valueFieldName, typeFieldName, labelFieldName);


    //             // This is a new item so do a DB insert
    //             if (itemId == null || resetFields) {
    //                 contentValues.put(ContactsContract.Data.RAW_CONTACT_ID, rawId);
    //                 contentValues.put(ContactsContract.Data.MIMETYPE, contentItemType);

    //                 ops.add(ContentProviderOperation.newInsert(
    //                        contentUri).withValues(contentValues).build());
    //             }
    //             // This is an existing item so do a DB update
    //             else {
    //                 ops.add(ContentProviderOperation.newUpdate(contentUri)
    //                         .withSelection(BaseColumns._ID + "=? AND " +
    //                                 ContactsContract.Data.MIMETYPE + "=?",
    //                                 new String[] { itemId, contentItemType })
    //                         .withValues(contentValues)
    //                             .build());
    //             }
    //         }
    //     }
    // }

    private ContentValues buildContentValues(JSONObject item, String mimetype,
        SparseArray<String> typesMap, String[] fieldNames) {

        ContentValues contentValues = new ContentValues();

        if (mimetype == StructuredPostal.CONTENT_ITEM_TYPE) {
            contentValues.put(fieldNames[0], getJsonString(item, "formatted"));

            contentValues.put(fieldNames[3], getJsonString(item, "streetAddress"));

            contentValues.put(fieldNames[4], getJsonString(item, "locality"));
            contentValues.put(fieldNames[5], getJsonString(item, "region"));
            contentValues.put(fieldNames[6], getJsonString(item, "postalCode"));
            contentValues.put(fieldNames[7], getJsonString(item, "country"));

        } else if (mimetype == Organization.CONTENT_ITEM_TYPE) {
            contentValues.put(fieldNames[0], getJsonString(item, "name"));
            contentValues.put(fieldNames[3], getJsonString(item, "title"));
            contentValues.put(fieldNames[4], getJsonString(item, "department"));
        } else if (mimetype == Photo.CONTENT_ITEM_TYPE) {
            byte[] bytes = getPhotoBytes(item);
            contentValues.put(ContactsContract.Data.IS_SUPER_PRIMARY, 1);
            contentValues.put(Photo.PHOTO, bytes);

        } else {
            contentValues.put(fieldNames[0], getJsonString(item, "value"));
        }

        String rawType = getJsonString(item, "type");
        int type;
        boolean setCustom = false;
        if (mimetype == Im.CONTENT_ITEM_TYPE) {
            type = getProtocol(typesMap, rawType);

            setCustom = type == Im.PROTOCOL_CUSTOM ;
        } else {
            type = getType(typesMap, rawType);
            setCustom = type == ContactsContract.CommonDataKinds.BaseTypes.TYPE_CUSTOM;
        }

        contentValues.put(fieldNames[1], type);
        if (setCustom) {
            contentValues.put(fieldNames[2], rawType);
        }

        return contentValues;
    }


    // /**
    //  * Add content to a list of database actions to be performed
    //  *
    //  * @param ops the list of database actions
    //  * @param im the item to be inserted
    //  * @throws JSONException
    //  */
    // private void insertContent(ArrayList<ContentProviderOperation> ops, Uri contentUri,
    //     JSONArray items, String contentItemType, SparseArray<String> typesMap,
    //     String nameFieldName, String typeFieldName, String labelFieldName) throws JSONException {

    //     if (items != null) {
    //         for (int i = 0; i < items.length(); i++) {
    //             JSONObject item = (JSONObject) items.get(i);

    //             ContentValues contentValues = buildContentValues(item, typesMap,
    //                         nameFieldName, typeFieldName, labelFieldName);

    //             // ArrayList<ContentProviderOperation> ops, JSONObject im, Uri contentUri) {
    //             ContentProviderOperation.Builder builder = ContentProviderOperation.newInsert(contentUri);
    //             builder.withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, 0);
    //             builder.withValue(ContactsContract.Data.MIMETYPE, contentItemType);

    //             builder.withValues(contentValues);

    //             ops.add(builder.build());
    //         }
    //     }

    // }



    /**
     * Add a website to a list of database actions to be performed
     *
     * @param ops the list of database actions
     * @param website the item to be inserted
     */
    private void insertWebsite(ArrayList<ContentProviderOperation> ops,
            JSONObject website, Uri contentUri) {
        ops.add(ContentProviderOperation.newInsert(contentUri)
                .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, 0)
                .withValue(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.Website.CONTENT_ITEM_TYPE)
                .withValue(ContactsContract.CommonDataKinds.Website.DATA, getJsonString(website, "value"))
                .withValue(ContactsContract.CommonDataKinds.Website.TYPE, getContactType(getJsonString(website, "type")))
                .build());
    }

    /**
     * Add an im to a list of database actions to be performed
     *
     * @param ops the list of database actions
     * @param im the item to be inserted
     */
    private void insertIm(ArrayList<ContentProviderOperation> ops, JSONObject im, Uri contentUri) {
        ContentProviderOperation.Builder builder = ContentProviderOperation.newInsert(contentUri);
        builder.withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, 0);
        builder.withValue(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.Im.CONTENT_ITEM_TYPE);
        builder.withValue(ContactsContract.CommonDataKinds.Im.DATA, getJsonString(im, "value"));

        String imType = getJsonString(im, "type");
        int imTypeCode = getImType(imType);
        builder.withValue(ContactsContract.CommonDataKinds.Im.PROTOCOL, imTypeCode);

        if (imTypeCode == ContactsContract.CommonDataKinds.Im.PROTOCOL_CUSTOM) {
            builder.withValue(ContactsContract.CommonDataKinds.Im.CUSTOM_PROTOCOL, imType);
        }

        ops.add(builder.build());
    }

    /**
     * Add an organization to a list of database actions to be performed
     *
     * @param ops the list of database actions
     * @param org the item to be inserted
     */
    private void insertOrganization(ArrayList<ContentProviderOperation> ops,
            JSONObject org, Uri contentUri) {
        ops.add(ContentProviderOperation.newInsert(contentUri)
                .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, 0)
                .withValue(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.Organization.CONTENT_ITEM_TYPE)
                .withValue(ContactsContract.CommonDataKinds.Organization.TYPE, getOrgType(getJsonString(org, "type")))
                .withValue(ContactsContract.CommonDataKinds.Organization.DEPARTMENT, getJsonString(org, "department"))
                .withValue(ContactsContract.CommonDataKinds.Organization.COMPANY, getJsonString(org, "name"))
                .withValue(ContactsContract.CommonDataKinds.Organization.TITLE, getJsonString(org, "title"))
                .build());
    }

    /**
     * Add an address to a list of database actions to be performed
     *
     * @param ops the list of database actions
     * @param address the item to be inserted
     */
    private void insertAddress(ArrayList<ContentProviderOperation> ops,
            JSONObject address, Uri contentUri) {
        ops.add(ContentProviderOperation.newInsert(contentUri)
                .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, 0)
                .withValue(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.StructuredPostal.CONTENT_ITEM_TYPE)
                .withValue(ContactsContract.CommonDataKinds.StructuredPostal.TYPE, getAddressType(getJsonString(address, "type")))
                .withValue(ContactsContract.CommonDataKinds.StructuredPostal.FORMATTED_ADDRESS, getJsonString(address, "formatted"))
                .withValue(ContactsContract.CommonDataKinds.StructuredPostal.STREET, getJsonString(address, "streetAddress"))
                .withValue(ContactsContract.CommonDataKinds.StructuredPostal.CITY, getJsonString(address, "locality"))
                .withValue(ContactsContract.CommonDataKinds.StructuredPostal.REGION, getJsonString(address, "region"))
                .withValue(ContactsContract.CommonDataKinds.StructuredPostal.POSTCODE, getJsonString(address, "postalCode"))
                .withValue(ContactsContract.CommonDataKinds.StructuredPostal.COUNTRY, getJsonString(address, "country"))
                .build());
    }

    /**
     * Add an email to a list of database actions to be performed
     *
     * @param ops the list of database actions
     * @param email the item to be inserted
     */
    private void insertEmail(ArrayList<ContentProviderOperation> ops,
            JSONObject email, Uri contentUri) {
        ops.add(ContentProviderOperation.newInsert(contentUri)
                .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, 0)
                .withValue(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.Email.CONTENT_ITEM_TYPE)
                .withValue(ContactsContract.CommonDataKinds.Email.DATA, getJsonString(email, "value"))
                .withValue(ContactsContract.CommonDataKinds.Email.TYPE, getContactType(getJsonString(email, "type")))
                .build());
    }

    /**
     * Add a phone to a list of database actions to be performed
     *
     * @param ops the list of database actions
     * @param phone the item to be inserted
     */
    private void insertPhone(ArrayList<ContentProviderOperation> ops,
            JSONObject phone, Uri contentUri) {
        ops.add(ContentProviderOperation.newInsert(contentUri)
                .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, 0)
                .withValue(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE)
                .withValue(ContactsContract.CommonDataKinds.Phone.NUMBER, getJsonString(phone, "value"))
                .withValue(ContactsContract.CommonDataKinds.Phone.TYPE, getPhoneType(getJsonString(phone, "type")))
                .build());
    }

    /**
     * Add a phone to a list of database actions to be performed
     *
     * @param ops the list of database actions
     * @param phone the item to be inserted
     */
    private void insertPhoto(ArrayList<ContentProviderOperation> ops,
            JSONObject photo, Uri contentUri) {
        byte[] bytes = getPhotoBytes(photo);
        ops.add(ContentProviderOperation.newInsert(contentUri)
                .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, 0)
                .withValue(ContactsContract.Data.IS_SUPER_PRIMARY, 1)
                .withValue(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.Photo.CONTENT_ITEM_TYPE)
                .withValue(ContactsContract.CommonDataKinds.Photo.PHOTO, bytes)
                .build());
    }


    /**
     * Gets the raw bytes from the supplied photo json object.
     *
     * @param filename the file to read the bytes from
     * @return a byte array
     * @throws IOException
     */
    private byte[] getPhotoBytes(JSONObject photo) {
        String type = getJsonString(photo, "type");
        String value = getJsonString(photo, "value");
        if ("url".equals(type)) {
            return getPhotoBytes(value);

        } else if ("base64".equals(type)) {
            return Base64.decode(value, Base64.DEFAULT);

        } else {
            return null;
        }
    }

    /**
     * Gets the raw bytes from the supplied filename
     *
     * @param filename the file to read the bytes from
     * @return a byte array
     * @throws IOException
     */
    private byte[] getPhotoBytes(String filename) {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        try {
            int bytesRead = 0;
            long totalBytesRead = 0;
            byte[] data = new byte[8192];
            InputStream in = getPathFromUri(filename);

            while ((bytesRead = in.read(data, 0, data.length)) != -1 && totalBytesRead <= MAX_PHOTO_SIZE) {
                buffer.write(data, 0, bytesRead);
                totalBytesRead += bytesRead;
            }

            in.close();
            buffer.flush();
        } catch (FileNotFoundException e) {
            Log.e(LOG_TAG, e.getMessage(), e);
        } catch (IOException e) {
            Log.e(LOG_TAG, e.getMessage(), e);
        }
        return buffer.toByteArray();
    }

    /**
       * Get an input stream based on file path or uri content://, http://, file://
       *
       * @param path
       * @return an input stream
     * @throws IOException
       */
    private InputStream getPathFromUri(String path) throws IOException {
        if (path.startsWith("content:")) {
            Uri uri = Uri.parse(path);
            return mApp.getActivity().getContentResolver().openInputStream(uri);
        }
        if (path.startsWith("http:") || path.startsWith("https:") || path.startsWith("file:")) {
            URL url = new URL(path);
            return url.openStream();
        }
        else {
            return new FileInputStream(path);
        }
    }

    // /**
    //  * Creates a new contact and stores it in the database
    //  *
    //  * @param contact the contact to be saved
    //  * @param account the account to be saved under
    //  */
    // private String createNewContact(JSONObject contact, String accountType, String accountName, boolean callerIsSyncAdapter) {
    //     // Create a list of attributes to add to the contact database
    //     ArrayList<ContentProviderOperation> ops = new ArrayList<ContentProviderOperation>();

    //     //Add contact type
    //     Uri contentUri = ContactsContract.RawContacts.CONTENT_URI;
    //     if (callerIsSyncAdapter) {
    //         contentUri = ContactsContract.RawContacts.CONTENT_URI.buildUpon()
    //             .appendQueryParameter(ContactsContract.CALLER_IS_SYNCADAPTER, "true")
    //             .build();
    //     }
    //     ContentProviderOperation.Builder builder = ContentProviderOperation.newInsert(contentUri);

    //     builder.withValue(ContactsContract.RawContacts.ACCOUNT_TYPE, accountType);
    //     builder.withValue(ContactsContract.RawContacts.ACCOUNT_NAME, accountName);

    //     String sourceId = getJsonString(contact, "sourceId");
    //     if (sourceId != null) {
    //             builder.withValue(ContactsContract.RawContacts.SOURCE_ID, sourceId);
    //     }

    //     int dirty = contact.optBoolean("dirty") ? 1 : 0 ;
    //     builder.withValue(ContactsContract.RawContacts.DIRTY, dirty);

    //     int deleted = contact.optBoolean("deleted") ? 1 : 0 ;
    //     builder.withValue(ContactsContract.RawContacts.DELETED, deleted);

    //     String sync1 = getJsonString(contact, "sync1");
    //     if (sync1 != null) {
    //         builder.withValue(ContactsContract.RawContacts.SYNC1, sync1);
    //     }

    //     String sync2 = getJsonString(contact, "sync2");
    //     if (sync2 != null) {
    //         builder.withValue(ContactsContract.RawContacts.SYNC2, sync2);
    //     }

    //     String sync3 = getJsonString(contact, "sync3");
    //     if (sync3 != null) {
    //         builder.withValue(ContactsContract.RawContacts.SYNC3, sync3);
    //     }

    //     String sync4 = getJsonString(contact, "sync4");
    //     if (sync4 != null) {
    //         builder.withValue(ContactsContract.RawContacts.SYNC4, sync4);
    //     }
    //     ops.add(builder.build());


    //     contentUri = ContactsContract.Data.CONTENT_URI;
    //     if (callerIsSyncAdapter) {
    //         contentUri = contentUri.buildUpon()
    //             .appendQueryParameter(ContactsContract.CALLER_IS_SYNCADAPTER, "true")
    //             .build();
    //     }

    //     // Add name
    //     try {
    //         JSONObject name = contact.optJSONObject("name");
    //         String displayName = contact.getString("displayName");
    //         if (displayName != null || name != null) {
    //             ops.add(ContentProviderOperation.newInsert(contentUri)
    //                     .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, 0)
    //                     .withValue(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE)
    //                     .withValue(ContactsContract.CommonDataKinds.StructuredName.DISPLAY_NAME, displayName)
    //                     .withValue(ContactsContract.CommonDataKinds.StructuredName.FAMILY_NAME, getJsonString(name, "familyName"))
    //                     .withValue(ContactsContract.CommonDataKinds.StructuredName.MIDDLE_NAME, getJsonString(name, "middleName"))
    //                     .withValue(ContactsContract.CommonDataKinds.StructuredName.GIVEN_NAME, getJsonString(name, "givenName"))
    //                     .withValue(ContactsContract.CommonDataKinds.StructuredName.PREFIX, getJsonString(name, "honorificPrefix"))
    //                     .withValue(ContactsContract.CommonDataKinds.StructuredName.SUFFIX, getJsonString(name, "honorificSuffix"))
    //                     .build());
    //         }
    //     } catch (JSONException e) {
    //         Log.d(LOG_TAG, "Could not get name object");
    //     }

    //     //Add phone numbers
    //     JSONArray phones = null;
    //     try {
    //         phones = contact.getJSONArray("phoneNumbers");
    //         if (phones != null) {
    //             for (int i = 0; i < phones.length(); i++) {
    //                 JSONObject phone = (JSONObject) phones.get(i);
    //                 insertPhone(ops, phone, contentUri);
    //             }
    //         }
    //     } catch (JSONException e) {
    //         Log.d(LOG_TAG, "Could not get phone numbers");
    //     }

    //     // Add emails
    //     JSONArray emails = null;
    //     try {
    //         emails = contact.getJSONArray("emails");
    //         if (emails != null) {
    //             for (int i = 0; i < emails.length(); i++) {
    //                 JSONObject email = (JSONObject) emails.get(i);
    //                 insertEmail(ops, email, contentUri);
    //             }
    //         }
    //     } catch (JSONException e) {
    //         Log.d(LOG_TAG, "Could not get emails");
    //     }

    //     // Add addresses
    //     JSONArray addresses = null;
    //     try {
    //         addresses = contact.getJSONArray("addresses");
    //         if (addresses != null) {
    //             for (int i = 0; i < addresses.length(); i++) {
    //                 JSONObject address = (JSONObject) addresses.get(i);
    //                 insertAddress(ops, address, contentUri);
    //             }
    //         }
    //     } catch (JSONException e) {
    //         Log.d(LOG_TAG, "Could not get addresses");
    //     }

    //     // Add organizations
    //     JSONArray organizations = null;
    //     try {
    //         organizations = contact.getJSONArray("organizations");
    //         if (organizations != null) {
    //             for (int i = 0; i < organizations.length(); i++) {
    //                 JSONObject org = (JSONObject) organizations.get(i);
    //                 insertOrganization(ops, org, contentUri);
    //             }
    //         }
    //     } catch (JSONException e) {
    //         Log.d(LOG_TAG, "Could not get organizations");
    //     }

    //     // Add IMs
    //     JSONArray ims = null;
    //     try {
    //         ims = contact.getJSONArray("ims");
    //         if (ims != null) {
    //             for (int i = 0; i < ims.length(); i++) {
    //                 JSONObject im = (JSONObject) ims.get(i);
    //                 insertIm(ops, im, contentUri);
    //             }
    //         }
    //     } catch (JSONException e) {
    //         Log.d(LOG_TAG, "Could not get emails");
    //     }

    //     // Add note
    //     String note = getJsonString(contact, "note");
    //     if (note != null) {
    //         ops.add(ContentProviderOperation.newInsert(contentUri)
    //                 .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, 0)
    //                 .withValue(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.Note.CONTENT_ITEM_TYPE)
    //                 .withValue(ContactsContract.CommonDataKinds.Note.NOTE, note)
    //                 .build());
    //     }

    //     // Add nickname
    //     String nickname = getJsonString(contact, "nickname");
    //     if (nickname != null) {
    //         ops.add(ContentProviderOperation.newInsert(contentUri)
    //                 .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, 0)
    //                 .withValue(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.Nickname.CONTENT_ITEM_TYPE)
    //                 .withValue(ContactsContract.CommonDataKinds.Nickname.NAME, nickname)
    //                 .build());
    //     }

    //     // Add urls
    //     JSONArray websites = null;
    //     try {
    //         websites = contact.getJSONArray("urls");
    //         if (websites != null) {
    //             for (int i = 0; i < websites.length(); i++) {
    //                 JSONObject website = (JSONObject) websites.get(i);
    //                 insertWebsite(ops, website, contentUri);
    //             }
    //         }
    //     } catch (JSONException e) {
    //         Log.d(LOG_TAG, "Could not get websites");
    //     }

    //     // Add birthday
    //     String birthday = getJsonString(contact, "birthday");
    //     if (birthday != null) {
    //         ops.add(ContentProviderOperation.newInsert(contentUri)
    //                 .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, 0)
    //                 .withValue(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.Event.CONTENT_ITEM_TYPE)
    //                 .withValue(ContactsContract.CommonDataKinds.Event.TYPE, ContactsContract.CommonDataKinds.Event.TYPE_BIRTHDAY)
    //                 .withValue(ContactsContract.CommonDataKinds.Event.START_DATE, birthday)
    //                 .build());
    //     }

    //     // Add photos
    //     JSONArray photos = null;
    //     try {
    //         photos = contact.getJSONArray("photos");
    //         if (photos != null) {
    //             for (int i = 0; i < photos.length(); i++) {
    //                 JSONObject photo = (JSONObject) photos.get(i);
    //                 insertPhoto(ops, photo, contentUri);
    //             }
    //         }
    //     } catch (JSONException e) {
    //         Log.d(LOG_TAG, "Could not get photos");
    //     }


    //     // Add relations:
    //     JSONArray relations = null;
    //     try {
    //         relations = contact.getJSONArray("relations");
    //         insertContent(ops, contentUri, relations,
    //             ContactsContract.CommonDataKinds.Relation.CONTENT_ITEM_TYPE,
    //             RELATION_TYPES,
    //             ContactsContract.CommonDataKinds.Relation.NAME,
    //             ContactsContract.CommonDataKinds.Relation.TYPE,
    //             ContactsContract.CommonDataKinds.Relation.LABEL);
    //     } catch (JSONException e) {
    //         Log.d(LOG_TAG, "Could not get relations");
    //     }

    //     // Add events:
    //     JSONArray events = null;
    //     try {
    //         events = contact.getJSONArray("about");
    //         insertContent(ops, contentUri, events,
    //             ContactsContract.CommonDataKinds.Event.CONTENT_ITEM_TYPE,
    //             EVENT_TYPES,
    //             ContactsContract.CommonDataKinds.Event.START_DATE,
    //             ContactsContract.CommonDataKinds.Event.TYPE,
    //             ContactsContract.CommonDataKinds.Event.LABEL);
    //     } catch (JSONException e) {
    //         Log.d(LOG_TAG, "Could not get about");
    //     }


    //     String newId = null;
    //     //Add contact
    //     try {
    //         ContentProviderResult[] cpResults = mApp.getActivity().getContentResolver().applyBatch(ContactsContract.AUTHORITY, ops);
    //         if (cpResults.length >= 0) {
    //             newId = cpResults[0].uri.getLastPathSegment();
    //         }
    //     } catch (RemoteException e) {
    //         Log.e(LOG_TAG, e.getMessage(), e);
    //     } catch (OperationApplicationException e) {
    //         Log.e(LOG_TAG, e.getMessage(), e);
    //     }
    //     return newId;
    // }

    /**
     * This method will remove a Contact from the database based on ID.
     * @param id the unique ID of the contact to remove
     */
    @Override
    public boolean remove(String id) {
        return remove(id, false);
    }


    @Override
    /**
     * This method will remove a Contact from the database based on ID.
     * @param id the unique ID of the contact to remove (rawId)
     * @param callerIsSyncAdapter trully remove or just set DELETED and DIRTY falgs : See  http://developer.android.com/reference/android/provider/ContactsContract.RawContacts.html §delete .
     */
    public boolean remove(String rawId, boolean callerIsSyncAdapter) {
        Uri uri = ContactsContract.RawContacts.CONTENT_URI;
        if (callerIsSyncAdapter) {
            uri = uri.buildUpon().appendQueryParameter(
                    ContactsContract.CALLER_IS_SYNCADAPTER, "true").build();
        }

        int result = mApp.getActivity().getContentResolver().delete(
            uri,
            ContactsContract.RawContacts._ID + " = ?",
            new String[] { rawId });

        return (result > 0) ? true : false;
    }


    /**************************************************************************
     *
     * All methods below this comment are used to convert from JavaScript
     * text types to Android integer types and vice versa.
     *
     *************************************************************************/

    /**
     * Converts a string from the W3C Contact API to it's Android int value.
     * @param string
     * @return Android int value
     */
    private int getPhoneType(String string) {
        int type = ContactsContract.CommonDataKinds.Phone.TYPE_OTHER;
        if (string != null) {
            if ("home".equals(string.toLowerCase())) {
                return ContactsContract.CommonDataKinds.Phone.TYPE_HOME;
            }
            else if ("mobile".equals(string.toLowerCase())) {
                return ContactsContract.CommonDataKinds.Phone.TYPE_MOBILE;
            }
            else if ("work".equals(string.toLowerCase())) {
                return ContactsContract.CommonDataKinds.Phone.TYPE_WORK;
            }
            else if ("work fax".equals(string.toLowerCase())) {
                return ContactsContract.CommonDataKinds.Phone.TYPE_FAX_WORK;
            }
            else if ("home fax".equals(string.toLowerCase())) {
                return ContactsContract.CommonDataKinds.Phone.TYPE_FAX_HOME;
            }
            else if ("fax".equals(string.toLowerCase())) {
                return ContactsContract.CommonDataKinds.Phone.TYPE_FAX_WORK;
            }
            else if ("pager".equals(string.toLowerCase())) {
                return ContactsContract.CommonDataKinds.Phone.TYPE_PAGER;
            }
            else if ("other".equals(string.toLowerCase())) {
                return ContactsContract.CommonDataKinds.Phone.TYPE_OTHER;
            }
            else if ("car".equals(string.toLowerCase())) {
                return ContactsContract.CommonDataKinds.Phone.TYPE_CAR;
            }
            else if ("company main".equals(string.toLowerCase())) {
                return ContactsContract.CommonDataKinds.Phone.TYPE_COMPANY_MAIN;
            }
            else if ("isdn".equals(string.toLowerCase())) {
                return ContactsContract.CommonDataKinds.Phone.TYPE_ISDN;
            }
            else if ("main".equals(string.toLowerCase())) {
                return ContactsContract.CommonDataKinds.Phone.TYPE_MAIN;
            }
            else if ("other fax".equals(string.toLowerCase())) {
                return ContactsContract.CommonDataKinds.Phone.TYPE_OTHER_FAX;
            }
            else if ("radio".equals(string.toLowerCase())) {
                return ContactsContract.CommonDataKinds.Phone.TYPE_RADIO;
            }
            else if ("telex".equals(string.toLowerCase())) {
                return ContactsContract.CommonDataKinds.Phone.TYPE_TELEX;
            }
            else if ("work mobile".equals(string.toLowerCase())) {
                return ContactsContract.CommonDataKinds.Phone.TYPE_WORK_MOBILE;
            }
            else if ("work pager".equals(string.toLowerCase())) {
                return ContactsContract.CommonDataKinds.Phone.TYPE_WORK_PAGER;
            }
            else if ("assistant".equals(string.toLowerCase())) {
                return ContactsContract.CommonDataKinds.Phone.TYPE_ASSISTANT;
            }
            else if ("mms".equals(string.toLowerCase())) {
                return ContactsContract.CommonDataKinds.Phone.TYPE_MMS;
            }
            else if ("callback".equals(string.toLowerCase())) {
                return ContactsContract.CommonDataKinds.Phone.TYPE_CALLBACK;
            }
            else if ("tty ttd".equals(string.toLowerCase())) {
                return ContactsContract.CommonDataKinds.Phone.TYPE_TTY_TDD;
            }
            else if ("custom".equals(string.toLowerCase())) {
                return ContactsContract.CommonDataKinds.Phone.TYPE_CUSTOM;
            }
        }
        return type;
    }

    /**
     * getPhoneType converts an Android phone type into a string
     * @param type
     * @return phone type as string.
     */
    private String getPhoneType(int type) {
        String stringType;
        switch (type) {
        case ContactsContract.CommonDataKinds.Phone.TYPE_CUSTOM:
            stringType = "custom";
            break;
        case ContactsContract.CommonDataKinds.Phone.TYPE_FAX_HOME:
            stringType = "home fax";
            break;
        case ContactsContract.CommonDataKinds.Phone.TYPE_FAX_WORK:
            stringType = "work fax";
            break;
        case ContactsContract.CommonDataKinds.Phone.TYPE_HOME:
            stringType = "home";
            break;
        case ContactsContract.CommonDataKinds.Phone.TYPE_MOBILE:
            stringType = "mobile";
            break;
        case ContactsContract.CommonDataKinds.Phone.TYPE_PAGER:
            stringType = "pager";
            break;
        case ContactsContract.CommonDataKinds.Phone.TYPE_WORK:
            stringType = "work";
            break;
        case ContactsContract.CommonDataKinds.Phone.TYPE_CALLBACK:
            stringType = "callback";
            break;
        case ContactsContract.CommonDataKinds.Phone.TYPE_CAR:
            stringType = "car";
            break;
        case ContactsContract.CommonDataKinds.Phone.TYPE_COMPANY_MAIN:
            stringType = "company main";
            break;
        case ContactsContract.CommonDataKinds.Phone.TYPE_OTHER_FAX:
            stringType = "other fax";
            break;
        case ContactsContract.CommonDataKinds.Phone.TYPE_RADIO:
            stringType = "radio";
            break;
        case ContactsContract.CommonDataKinds.Phone.TYPE_TELEX:
            stringType = "telex";
            break;
        case ContactsContract.CommonDataKinds.Phone.TYPE_TTY_TDD:
            stringType = "tty tdd";
            break;
        case ContactsContract.CommonDataKinds.Phone.TYPE_WORK_MOBILE:
            stringType = "work mobile";
            break;
        case ContactsContract.CommonDataKinds.Phone.TYPE_WORK_PAGER:
            stringType = "work pager";
            break;
        case ContactsContract.CommonDataKinds.Phone.TYPE_ASSISTANT:
            stringType = "assistant";
            break;
        case ContactsContract.CommonDataKinds.Phone.TYPE_MMS:
            stringType = "mms";
            break;
        case ContactsContract.CommonDataKinds.Phone.TYPE_ISDN:
            stringType = "isdn";
            break;
        case ContactsContract.CommonDataKinds.Phone.TYPE_OTHER:
        default:
            stringType = "other";
            break;
        }
        return stringType;
    }

    /**
     * Converts a string from the W3C Contact API to it's Android int value.
     * @param string
     * @return Android int value
     */
    private int getContactType(String string) {
        int type = ContactsContract.CommonDataKinds.Email.TYPE_OTHER;
        if (string != null) {
            if ("home".equals(string.toLowerCase())) {
                return ContactsContract.CommonDataKinds.Email.TYPE_HOME;
            }
            else if ("work".equals(string.toLowerCase())) {
                return ContactsContract.CommonDataKinds.Email.TYPE_WORK;
            }
            else if ("other".equals(string.toLowerCase())) {
                return ContactsContract.CommonDataKinds.Email.TYPE_OTHER;
            }
            else if ("mobile".equals(string.toLowerCase())) {
                return ContactsContract.CommonDataKinds.Email.TYPE_MOBILE;
            }
            else if ("custom".equals(string.toLowerCase())) {
                return ContactsContract.CommonDataKinds.Email.TYPE_CUSTOM;
            }
        }
        return type;
    }

    /**
     * getPhoneType converts an Android phone type into a string
     * @param type
     * @return phone type as string.
     */
    private String getContactType(int type) {
        String stringType;
        switch (type) {
        case ContactsContract.CommonDataKinds.Email.TYPE_CUSTOM:
            stringType = "custom";
            break;
        case ContactsContract.CommonDataKinds.Email.TYPE_HOME:
            stringType = "home";
            break;
        case ContactsContract.CommonDataKinds.Email.TYPE_WORK:
            stringType = "work";
            break;
        case ContactsContract.CommonDataKinds.Email.TYPE_MOBILE:
            stringType = "mobile";
            break;
        case ContactsContract.CommonDataKinds.Email.TYPE_OTHER:
        default:
            stringType = "other";
            break;
        }
        return stringType;
    }

    /**
     * Converts a string from the W3C Contact API to it's Android int value.
     * @param string
     * @return Android int value
     */
    private int getOrgType(String string) {
        int type = ContactsContract.CommonDataKinds.Organization.TYPE_OTHER;
        if (string != null) {
            if ("work".equals(string.toLowerCase())) {
                return ContactsContract.CommonDataKinds.Organization.TYPE_WORK;
            }
            else if ("other".equals(string.toLowerCase())) {
                return ContactsContract.CommonDataKinds.Organization.TYPE_OTHER;
            }
            else if ("custom".equals(string.toLowerCase())) {
                return ContactsContract.CommonDataKinds.Organization.TYPE_CUSTOM;
            }
        }
        return type;
    }

    /**
     * getPhoneType converts an Android phone type into a string
     * @param type
     * @return phone type as string.
     */
    private String getOrgType(int type) {
        String stringType;
        switch (type) {
        case ContactsContract.CommonDataKinds.Organization.TYPE_CUSTOM:
            stringType = "custom";
            break;
        case ContactsContract.CommonDataKinds.Organization.TYPE_WORK:
            stringType = "work";
            break;
        case ContactsContract.CommonDataKinds.Organization.TYPE_OTHER:
        default:
            stringType = "other";
            break;
        }
        return stringType;
    }

    /**
     * Converts a string from the W3C Contact API to it's Android int value.
     * @param string
     * @return Android int value
     */
    private int getAddressType(String string) {
        int type = ContactsContract.CommonDataKinds.StructuredPostal.TYPE_OTHER;
        if (string != null) {
            if ("work".equals(string.toLowerCase())) {
                return ContactsContract.CommonDataKinds.StructuredPostal.TYPE_WORK;
            }
            else if ("other".equals(string.toLowerCase())) {
                return ContactsContract.CommonDataKinds.StructuredPostal.TYPE_OTHER;
            }
            else if ("home".equals(string.toLowerCase())) {
                return ContactsContract.CommonDataKinds.StructuredPostal.TYPE_HOME;
            }
        }
        return type;
    }

    /**
     * getPhoneType converts an Android phone type into a string
     * @param type
     * @return phone type as string.
     */
    private String getAddressType(int type) {
        String stringType;
        switch (type) {
        case ContactsContract.CommonDataKinds.StructuredPostal.TYPE_HOME:
            stringType = "home";
            break;
        case ContactsContract.CommonDataKinds.StructuredPostal.TYPE_WORK:
            stringType = "work";
            break;
        case ContactsContract.CommonDataKinds.StructuredPostal.TYPE_OTHER:
        default:
            stringType = "other";
            break;
        }
        return stringType;
    }

    /**
     * Converts a string from the W3C Contact API to it's Android int value.
     * @param string
     * @return Android int value
     */
    private int getImType(String string) {
        int type = ContactsContract.CommonDataKinds.Im.PROTOCOL_CUSTOM;
        if (string != null) {
            if ("aim".equals(string.toLowerCase())) {
                return ContactsContract.CommonDataKinds.Im.PROTOCOL_AIM;
            }
            else if ("google talk".equals(string.toLowerCase())) {
                return ContactsContract.CommonDataKinds.Im.PROTOCOL_GOOGLE_TALK;
            }
            else if ("icq".equals(string.toLowerCase())) {
                return ContactsContract.CommonDataKinds.Im.PROTOCOL_ICQ;
            }
            else if ("jabber".equals(string.toLowerCase())) {
                return ContactsContract.CommonDataKinds.Im.PROTOCOL_JABBER;
            }
            else if ("msn".equals(string.toLowerCase())) {
                return ContactsContract.CommonDataKinds.Im.PROTOCOL_MSN;
            }
            else if ("netmeeting".equals(string.toLowerCase())) {
                return ContactsContract.CommonDataKinds.Im.PROTOCOL_NETMEETING;
            }
            else if ("qq".equals(string.toLowerCase())) {
                return ContactsContract.CommonDataKinds.Im.PROTOCOL_QQ;
            }
            else if ("skype".equals(string.toLowerCase())) {
                return ContactsContract.CommonDataKinds.Im.PROTOCOL_SKYPE;
            }
            else if ("yahoo".equals(string.toLowerCase())) {
                return ContactsContract.CommonDataKinds.Im.PROTOCOL_YAHOO;
            }
        }
        return type;
    }

    /**
     * getPhoneType converts an Android phone type into a string
     * @param type
     * @return phone type as string.
     */
    @SuppressWarnings("unused")
    private String getImType(int type) {
        String stringType;
        switch (type) {
        case ContactsContract.CommonDataKinds.Im.PROTOCOL_AIM:
            stringType = "AIM";
            break;
        case ContactsContract.CommonDataKinds.Im.PROTOCOL_GOOGLE_TALK:
            stringType = "Google Talk";
            break;
        case ContactsContract.CommonDataKinds.Im.PROTOCOL_ICQ:
            stringType = "ICQ";
            break;
        case ContactsContract.CommonDataKinds.Im.PROTOCOL_JABBER:
            stringType = "Jabber";
            break;
        case ContactsContract.CommonDataKinds.Im.PROTOCOL_MSN:
            stringType = "MSN";
            break;
        case ContactsContract.CommonDataKinds.Im.PROTOCOL_NETMEETING:
            stringType = "NetMeeting";
            break;
        case ContactsContract.CommonDataKinds.Im.PROTOCOL_QQ:
            stringType = "QQ";
            break;
        case ContactsContract.CommonDataKinds.Im.PROTOCOL_SKYPE:
            stringType = "Skype";
            break;
        case ContactsContract.CommonDataKinds.Im.PROTOCOL_YAHOO:
            stringType = "Yahoo";
            break;
        default:
            stringType = "custom";
            break;
        }
        return stringType;
    }



    /**
     * Converts a string to it's Android int value.
     * @param string
     * @return Android int value
     */
    private int getType(SparseArray<String> typesMap, String string) {
        int index = typesMap.indexOfValue(string);
        if (index < 0) {
            return ContactsContract.CommonDataKinds.BaseTypes.TYPE_CUSTOM;
        }

        return typesMap.keyAt(index);
    }

    /**
     * Converts a string to it's Android int value, for Im protocoles.
     * @param string
     * @return Android int value
     */
    private int getProtocol(SparseArray<String> typesMap, String string) {
        int index = typesMap.indexOfValue(string);
        if (index < 0) {
            return Im.PROTOCOL_CUSTOM;
        }
        return typesMap.keyAt(index);
    }

    /**
     * Converts an Android phone type into a string
     * @param type
     * @return relationtype as string.
     */
    private String getType(SparseArray<String> typesMap, int type) {
        return typesMap.get(type);
    }




    static final String[] PHONE_FIELDS = new String[] { Phone.NUMBER, Phone.TYPE, Phone.LABEL };

    static final SparseArray<String> PHONE_TYPES = new SparseArray<String>();
    static {
        PHONE_TYPES.append(Phone.TYPE_CUSTOM, "custom");
        PHONE_TYPES.append(Phone.TYPE_HOME, "home");
        PHONE_TYPES.append(Phone.TYPE_MOBILE, "mobile");
        PHONE_TYPES.append(Phone.TYPE_WORK, "work");
        PHONE_TYPES.append(Phone.TYPE_FAX_WORK, "work fax");
        PHONE_TYPES.append(Phone.TYPE_FAX_HOME, "home fax");
        PHONE_TYPES.append(Phone.TYPE_PAGER, "pager");
        PHONE_TYPES.append(Phone.TYPE_OTHER, "other");
        PHONE_TYPES.append(Phone.TYPE_CALLBACK, "callback");
        PHONE_TYPES.append(Phone.TYPE_CAR, "car");
        PHONE_TYPES.append(Phone.TYPE_COMPANY_MAIN, "company main");
        PHONE_TYPES.append(Phone.TYPE_ISDN, "isdn");
        PHONE_TYPES.append(Phone.TYPE_MAIN, "main");
        PHONE_TYPES.append(Phone.TYPE_OTHER_FAX, "other fax");
        PHONE_TYPES.append(Phone.TYPE_RADIO, "radio");
        PHONE_TYPES.append(Phone.TYPE_TELEX, "telex");
        PHONE_TYPES.append(Phone.TYPE_TTY_TDD, "tty tdd");
        PHONE_TYPES.append(Phone.TYPE_WORK_MOBILE, "work mobile");
        PHONE_TYPES.append(Phone.TYPE_WORK_PAGER, "work pager");
        PHONE_TYPES.append(Phone.TYPE_ASSISTANT, "assistant");
        PHONE_TYPES.append(Phone.TYPE_MMS, "mms");
    }

    static final String[] EMAIL_FIELDS = new String[] { Email.ADDRESS, Email.TYPE, Email.LABEL };
    static final String[] WEBSITE_FIELDS = new String[] { Website.URL, Website.TYPE, Website.LABEL };

    static final SparseArray<String> CONTACT_TYPES = new SparseArray<String>();
    static {
        CONTACT_TYPES.append(Email.TYPE_CUSTOM, "custom");
        CONTACT_TYPES.append(Email.TYPE_HOME, "home");
        CONTACT_TYPES.append(Email.TYPE_WORK, "work");
        CONTACT_TYPES.append(Email.TYPE_MOBILE, "mobile");
        CONTACT_TYPES.append(Email.TYPE_OTHER, "other");
    }

    static final String[] IM_FIELDS = new String[] { Im.DATA, Im.PROTOCOL, Im.CUSTOM_PROTOCOL };

    static final SparseArray<String> IM_TYPES = new SparseArray<String>();
    static {
        IM_TYPES.append(Im.PROTOCOL_CUSTOM, "custom");
        IM_TYPES.append(Im.PROTOCOL_AIM, "aim");
        IM_TYPES.append(Im.PROTOCOL_GOOGLE_TALK, "gtalk");
        IM_TYPES.append(Im.PROTOCOL_ICQ, "icq");
        IM_TYPES.append(Im.PROTOCOL_JABBER, "jabber");
        IM_TYPES.append(Im.PROTOCOL_MSN, "msb");
        IM_TYPES.append(Im.PROTOCOL_NETMEETING, "netmeeting");
        IM_TYPES.append(Im.PROTOCOL_QQ, "qq");
        IM_TYPES.append(Im.PROTOCOL_SKYPE, "skype");
        IM_TYPES.append(Im.PROTOCOL_YAHOO, "yahoo");
    }

    static final String[] RELATION_FIELDS = new String[] { Relation.NAME,
            Relation.TYPE, Relation.LABEL };

    public static SparseArray<String> RELATION_TYPES = new SparseArray<String>();
    static {
        RELATION_TYPES.append(Relation.TYPE_CUSTOM, "custom");
        RELATION_TYPES.append(Relation.TYPE_ASSISTANT, "assistant");
        RELATION_TYPES.append(Relation.TYPE_BROTHER, "brother");
        RELATION_TYPES.append(Relation.TYPE_CHILD, "child");
        RELATION_TYPES.append(Relation.TYPE_DOMESTIC_PARTNER, "domestic_partner");
        RELATION_TYPES.append(Relation.TYPE_FATHER, "father");
        RELATION_TYPES.append(Relation.TYPE_FRIEND, "friend");
        RELATION_TYPES.append(Relation.TYPE_MANAGER, "manager");
        RELATION_TYPES.append(Relation.TYPE_MOTHER, "mother");
        RELATION_TYPES.append(Relation.TYPE_PARENT, "parent");
        RELATION_TYPES.append(Relation.TYPE_PARTNER, "partner");
        RELATION_TYPES.append(Relation.TYPE_REFERRED_BY, "referred_by");
        RELATION_TYPES.append(Relation.TYPE_RELATIVE, "relative");
        RELATION_TYPES.append(Relation.TYPE_SISTER, "sister");
        RELATION_TYPES.append(Relation.TYPE_SPOUSE, "spouse");
    }

    static final String[] EVENT_FIELDS = new String[] { Event.START_DATE,
            Event.TYPE, Event.LABEL };
    static SparseArray<String> EVENT_TYPES = new SparseArray<String>();

    static {
        EVENT_TYPES.append(Event.TYPE_CUSTOM, "custom");
        EVENT_TYPES.append(Event.TYPE_ANNIVERSARY, "anniversary");
        EVENT_TYPES.append(Event.TYPE_OTHER, "other");
        EVENT_TYPES.append(Event.TYPE_BIRTHDAY, "birthday");
    }

    static final String[] ADDRESS_FIELDS = new String[] {
        StructuredPostal.FORMATTED_ADDRESS,
        StructuredPostal.TYPE, StructuredPostal.LABEL,
        StructuredPostal.STREET, StructuredPostal.CITY, StructuredPostal.REGION,
        StructuredPostal.POSTCODE, StructuredPostal.COUNTRY };

    static SparseArray<String> ADDRESS_TYPES = new SparseArray<String>();

    static {
        ADDRESS_TYPES.append(StructuredPostal.TYPE_CUSTOM, "custom");
        ADDRESS_TYPES.append(StructuredPostal.TYPE_HOME, "home");
        ADDRESS_TYPES.append(StructuredPostal.TYPE_WORK, "work");
        ADDRESS_TYPES.append(StructuredPostal.TYPE_OTHER, "other");
    }

    static final String[] ORG_FIELDS = new String[] { Organization.COMPANY,
            Organization.TYPE, Organization.LABEL,
            Organization.TITLE, Organization.DEPARTMENT };
    static SparseArray<String> ORG_TYPES = new SparseArray<String>();

    static {
        ORG_TYPES.append(Organization.TYPE_CUSTOM, "custom");
        ORG_TYPES.append(Organization.TYPE_WORK, "work");
        ORG_TYPES.append(Organization.TYPE_OTHER, "other");
    }

    private void addContactFieldOps(ArrayList<ContentProviderOperation> ops, Uri contentUri, int rawId, boolean resetFields,
        String value, String contentItemType, String fieldName) throws JSONException {
        if (value != null) {
            ContentProviderOperation.Builder builder;
            if (rawId == -1) {
                builder = ContentProviderOperation.newInsert(contentUri);
                builder.withValueBackReference(
                    ContactsContract.Data.RAW_CONTACT_ID, 0);
                builder.withValue(
                    ContactsContract.Data.MIMETYPE, contentItemType);
            } else {
                builder = ContentProviderOperation.newUpdate(contentUri)
                    .withSelection(ContactsContract.Data.RAW_CONTACT_ID + "=? AND " +
                        ContactsContract.Data.MIMETYPE + "=?",
                        new String[] { "" + rawId, contentItemType });
            }

            builder.withValue(fieldName, value);
            ops.add(builder.build());
        }
    }

    private void addContactFieldOps(ArrayList<ContentProviderOperation> ops, Uri contentUri, int rawId, boolean resetFields,
        JSONArray items, String contentItemType, SparseArray<String> typesMap, String[] fieldNames) throws JSONException {

        if (items != null) {
            // Delete all the
            if (rawId != -1 && (items.length() == 0 || resetFields)) {
                Log.d(LOG_TAG, "This means we should be deleting all the items.");
                ops.add(ContentProviderOperation.newDelete(contentUri)
                        .withSelection(ContactsContract.Data.RAW_CONTACT_ID + "=? AND " +
                                ContactsContract.Data.MIMETYPE + "=?",
                                new String[] { "" + rawId, contentItemType })
                        .build());
            }
            // Modify or add a items
            for (int i = 0; i < items.length(); i++) {
                JSONObject item = (JSONObject) items.get(i);

                String itemId = getJsonString(item, "id");
                ContentValues contentValues = buildContentValues(item,
                    contentItemType, typesMap, fieldNames);


                // This is a new contact, insert accordingly
                if (rawId == -1) {
                    ContentProviderOperation.Builder builder = ContentProviderOperation.newInsert(contentUri);
                    builder.withValueBackReference(
                        ContactsContract.Data.RAW_CONTACT_ID, 0);
                    contentValues.put(
                        ContactsContract.Data.MIMETYPE, contentItemType);
                    builder.withValues(contentValues);
                    ops.add(builder.build());

                }
                // This is a new item so do a DB insert
                else if (itemId == null || resetFields) {
                    contentValues.put(ContactsContract.Data.RAW_CONTACT_ID, rawId);
                    contentValues.put(ContactsContract.Data.MIMETYPE, contentItemType);

                    ops.add(ContentProviderOperation.newInsert(
                           contentUri).withValues(contentValues).build());
                }
                // This is an existing item so do a DB update
                else {
                    ops.add(ContentProviderOperation.newUpdate(contentUri)
                            .withSelection(BaseColumns._ID + "=? AND " +
                                    ContactsContract.Data.MIMETYPE + "=?",
                                    new String[] { itemId, contentItemType })
                            .withValues(contentValues)
                                .build());
                }
            }
        }
    }

}
