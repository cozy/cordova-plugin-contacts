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
        columnsToFetch.add(RawContacts._ID);

        addColumnsToFetch(columnsToFetch, SYNC_FIELDS);

        for (String key : FIELDS_MAP.keySet()) {
            if (isRequired(key, populate)) {
                addColumnsToFetch(columnsToFetch, FIELDS_MAP.get(key));
            }
        }
        if (isRequired("birthday", populate)) {
            addColumnsToFetch(columnsToFetch, EVENT_FIELDS);
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

    private void addColumnsToFetch(HashSet<String> columnsToFetch,
        String[] columnNames) {
        columnsToFetch.add(BaseColumns._ID);
        for (String columnName : columnNames) {
            columnsToFetch.add(columnName);
        }
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
                    else if (mimetype.equals(Phone.CONTENT_ITEM_TYPE)
                            && isRequired("phoneNumbers", populate)) {
                        phones.put(
                            contactFieldQuery(c, PHONE_TYPES, PHONE_FIELDS));
                    }
                    else if (mimetype.equals(Email.CONTENT_ITEM_TYPE)
                            && isRequired("emails", populate)) {
                        emails.put(
                            contactFieldQuery(c, CONTACT_TYPES, EMAIL_FIELDS));
                    }
                    else if (mimetype.equals(StructuredPostal.CONTENT_ITEM_TYPE)
                            && isRequired("addresses", populate)) {
                        addresses.put(addressQuery(c)); // TODO
                    }
                    else if (mimetype.equals(Organization.CONTENT_ITEM_TYPE)
                            && isRequired("organizations", populate)) {
                        organizations.put(organizationQuery(c)); // TODO
                    }
                    else if (mimetype.equals(Im.CONTENT_ITEM_TYPE)
                            && isRequired("ims", populate)) {
                        ims.put(
                            contactFieldQuery(c, IM_TYPES, IM_FIELDS));
                    }
                    else if (mimetype.equals(Note.CONTENT_ITEM_TYPE)
                            && isRequired("note", populate)) {
                        contact.put("note", c.getString(colNote));
                    }
                    else if (mimetype.equals(Nickname.CONTENT_ITEM_TYPE)
                            && isRequired("nickname", populate)) {
                        contact.put("nickname", c.getString(colNickname));
                    }
                    else if (mimetype.equals(Website.CONTENT_ITEM_TYPE)
                            && isRequired("urls", populate)) {
                        websites.put(
                            contactFieldQuery(c, CONTACT_TYPES, WEBSITE_FIELDS));

                    }
                    else if (mimetype.equals(Event.CONTENT_ITEM_TYPE)) {
                        if (isRequired("birthday", populate) &&
                            Event.TYPE_BIRTHDAY == c.getInt(colEventType) &&
                            !contact.has("birthday")) {
                            contact.put("birthday", c.getString(colBirthday));
                        } else if (isRequired("about", populate)) {
                            about.put(
                                contactFieldQuery(c, EVENT_TYPES, EVENT_FIELDS));
                        }
                    }
                    else if (mimetype.equals(Photo.CONTENT_ITEM_TYPE)
                            && isRequired("photos", populate)) {
                        JSONObject photo = photoQuery(c, rawId);
                        if (photo != null) {
                            photos.put(photo);
                        }
                    }
                    else if (mimetype.equals(Relation.CONTENT_ITEM_TYPE)
                        && isRequired("relations", populate)) {
                        relations.put(
                            contactFieldQuery(c, RELATION_TYPES, RELATION_FIELDS));
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

        } else {
            if (isWildCardSearch(fields)) {
                // TODO : duplicate some query ! (.value ones)
                fields = new JSONArray(dbMap.keySet());
            }

            String key, baseKey;
            try {
                for (int i = 0; i < fields.length(); i++) {
                    key = fields.getString(i);
                    baseKey = key.split("\\.")[0];

                    if (key.equals("id")) {
                        where.add("(" + dbMap.get(key) + " = ? )");
                        whereArgs.add(searchTerm.substring(1, searchTerm.length() - 1));
                    }
                    // mimtype fields
                    else if (CONTENT_ITEM_TYPES_MAP.containsKey(baseKey)) {
                        where.add("(" + dbMap.get(key) + " LIKE ? AND "
                                + ContactsContract.Data.MIMETYPE + " = ? )");
                        whereArgs.add(searchTerm);
                        whereArgs.add(CONTENT_ITEM_TYPES_MAP.get(baseKey));
                    }
                    else {
                        where.add("(" + dbMap.get(key) + " LIKE ? )");
                        whereArgs.add(searchTerm);

                    }

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
        JSONObject organization = contactFieldQuery(cursor, ORG_TYPES, ORG_FIELDS);
        try {
            organization.remove("value");
            organization.put("department", cursor.getString(cursor.getColumnIndex(Organization.DEPARTMENT)));
            organization.put("name", cursor.getString(cursor.getColumnIndex(Organization.COMPANY)));
            organization.put("title", cursor.getString(cursor.getColumnIndex(Organization.TITLE)));
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
        JSONObject address = contactFieldQuery(cursor, CONTACT_TYPES, ADDRESS_FIELDS);
        try {
            address.remove("value");
            address.put("formatted", cursor.getString(cursor.getColumnIndex(StructuredPostal.FORMATTED_ADDRESS)));
            address.put("streetAddress", cursor.getString(cursor.getColumnIndex(StructuredPostal.STREET)));
            address.put("locality", cursor.getString(cursor.getColumnIndex(StructuredPostal.CITY)));
            address.put("region", cursor.getString(cursor.getColumnIndex(StructuredPostal.REGION)));
            address.put("postalCode", cursor.getString(cursor.getColumnIndex(StructuredPostal.POSTCODE)));
            address.put("country", cursor.getString(cursor.getColumnIndex(StructuredPostal.COUNTRY)));
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

    private JSONObject contactFieldQuery(Cursor c,
        SparseArray<String> typesMap, String[] fieldNames) {
        JSONObject contactField = new JSONObject();
        try {
            contactField.put("id", c.getString(c.getColumnIndex(BaseColumns._ID)));
            contactField.put("pref", false); // Android does not store pref attribute
            contactField.put("value", c.getString(c.getColumnIndex(fieldNames[0])));
            String type = getType(typesMap, c.getInt(c.getColumnIndex(fieldNames[1])));

            if ("custom".equals(type)) {
                type = c.getString(c.getColumnIndex(fieldNames[2]));
            }
            contactField.put("type", type);
        } catch (JSONException e) {
            Log.e(LOG_TAG, e.getMessage(), e);
        } catch (Exception excp) {
            Log.e(LOG_TAG, excp.getMessage(), excp);
        }
        return contactField;
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

            if (displayName != null) {
                nameValues.put(StructuredName.DISPLAY_NAME, displayName);
            }

            name = contact.getJSONObject("name");
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

        if (nameValues.size() > 0) {
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
        }



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

        for (String key : new String[] {
            "photos", "phoneNumbers", "emails", "addresses", "organizations",
            "ims", "urls", "relations", "about"
            }) {
            JSONArray items = null;
            try {
                items = contact.getJSONArray(key);
                addContactFieldOps(ops, contentUri, rawId, resetFields,
                    items,
                    CONTENT_ITEM_TYPES_MAP.get(key),
                    TYPES_MAP.get(key),
                    FIELDS_MAP.get(key));
            } catch (JSONException e) {
                Log.d(LOG_TAG, "Could not get " + key);
            }
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
            return contentValues;

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


    /**************************************************************************
     *
     * All methods below this comment are used to convert from JavaScript
     * text types to Android integer types and vice versa.
     *
     *************************************************************************/


    /**
     * Converts a string to it's Android int value.
     * @param string
     * @return Android int value
     */
    private int getType(SparseArray<String> typesMap, String type) {
        if (type == null) {
            type = "other";
        }
        int index = typesMap.indexOfValue(type);
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

    static final String[] SYNC_FIELDS = new String[] {
        ContactsContract.Data.MIMETYPE,
        RawContacts.VERSION,
        RawContacts.DIRTY,
        RawContacts.DELETED,
        RawContacts.SOURCE_ID,
        RawContacts.SYNC1,
        RawContacts.SYNC2,
        RawContacts.SYNC3,
        RawContacts.SYNC4
    };

    static final String[] NAME_FIELDS = new String[] {
        StructuredName.DISPLAY_NAME,
        StructuredName.FAMILY_NAME,
        StructuredName.GIVEN_NAME,
        StructuredName.MIDDLE_NAME,
        StructuredName.PREFIX,
        StructuredName.SUFFIX
    };

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


    private static final Map<String, String> CONTENT_ITEM_TYPES_MAP = new HashMap<String, String>();
    static {
        CONTENT_ITEM_TYPES_MAP.put("name", StructuredName.CONTENT_ITEM_TYPE);
        CONTENT_ITEM_TYPES_MAP.put("note", Note.CONTENT_ITEM_TYPE);
        CONTENT_ITEM_TYPES_MAP.put("nickname", Nickname.CONTENT_ITEM_TYPE);
        CONTENT_ITEM_TYPES_MAP.put("photos", Photo.CONTENT_ITEM_TYPE);
        CONTENT_ITEM_TYPES_MAP.put("phoneNumbers", Phone.CONTENT_ITEM_TYPE);
        CONTENT_ITEM_TYPES_MAP.put("emails", Email.CONTENT_ITEM_TYPE);
        CONTENT_ITEM_TYPES_MAP.put("addresses", StructuredPostal.CONTENT_ITEM_TYPE);
        CONTENT_ITEM_TYPES_MAP.put("organizations", Organization.CONTENT_ITEM_TYPE);
        CONTENT_ITEM_TYPES_MAP.put("ims", Im.CONTENT_ITEM_TYPE);
        CONTENT_ITEM_TYPES_MAP.put("urls", Website.CONTENT_ITEM_TYPE);
        CONTENT_ITEM_TYPES_MAP.put("relations", Relation.CONTENT_ITEM_TYPE);
        CONTENT_ITEM_TYPES_MAP.put("about", Event.CONTENT_ITEM_TYPE);

    }

    private static final Map<String, SparseArray<String>> TYPES_MAP = new HashMap<String, SparseArray<String>>();
    static {
        // CONTENT_ITEM_TYPES_MAP.put("name", );
        // CONTENT_ITEM_TYPES_MAP.put("note", );
        // CONTENT_ITEM_TYPES_MAP.put("nickname", );
        TYPES_MAP.put("photos", new SparseArray<String>());
        TYPES_MAP.put("phoneNumbers", PHONE_TYPES);
        TYPES_MAP.put("emails", CONTACT_TYPES);
        TYPES_MAP.put("addresses", ADDRESS_TYPES);
        TYPES_MAP.put("organizations", ORG_TYPES);
        TYPES_MAP.put("ims", IM_TYPES);
        TYPES_MAP.put("urls", CONTACT_TYPES);
        TYPES_MAP.put("relations", RELATION_TYPES);
        TYPES_MAP.put("about", EVENT_TYPES);
    }

    private static final Map<String, String[]> FIELDS_MAP = new HashMap<String, String[]>();
    static {
        FIELDS_MAP.put("displayName", new String[] { StructuredName.DISPLAY_NAME });
        FIELDS_MAP.put("name", NAME_FIELDS);
        FIELDS_MAP.put("phoneNumbers", PHONE_FIELDS);
        FIELDS_MAP.put("emails", EMAIL_FIELDS);
        FIELDS_MAP.put("addresses", ADDRESS_FIELDS);
        FIELDS_MAP.put("organizations", ORG_FIELDS);
        FIELDS_MAP.put("ims", IM_FIELDS);
        FIELDS_MAP.put("note", new String[] { Note.NOTE });
        FIELDS_MAP.put("nickname", new String[] { Nickname.NAME });
        FIELDS_MAP.put("urls", WEBSITE_FIELDS);
        FIELDS_MAP.put("about", EVENT_FIELDS);
        FIELDS_MAP.put("photos", new String[] { Photo.PHOTO });
        FIELDS_MAP.put("relations", RELATION_FIELDS);
    }


}
