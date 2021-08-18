# REST API

The HTTP methods that are LRS-specific are given in [the xAPI spec](https://github.com/adlnet/xAPI-Spec/blob/master/xAPI-Communication.md#datatransfer). Requests to the LRS (which are denoted by the `xapi` path prefix) must contain a Base64 encoded, colon-separated public and secret API key pair in the `authorization` field of the header. For example (assuming `http://example` is the URL body), `http://example.org/xapi/statements` is the URL at which the user inserts and queries xAPI statements; other URLs are used to insert and query documents, agents, and activities.

In addition to the LRS HTTP methods, lrsql supports methods for admin account creation, login, and use; these methods are denoted by the `admin` path prefix.

## Admin Account Routes

The following examples use `http://example.org` as the URL body. All methods require that the request body is a JSON object that contains `username` and `password` string values; otherwise, a `400 BAD REQUEST` response is returned. All methods return `200 OK` on success.

- `POST http://example.org/admin/account/create`: Create a new admin account. The response body contains a newly generated JSON Web Token on success. Returns a `400` error if the request body parameters are invalid, or a `409 CONFLICT` error if the account already exists.
- `POST http://example.org/admin/account/login`: Log into an existing account. The response body contains a newly generated JSON Web Token on success. A `404 NOT FOUND` error is returned if the account does not exist, or a `401 FORBIDDEN` error if the password is incorrect.
- `DELETE http://example.org/admin/account`: Delete an existing account. The response body is a message that says `"Successfully deleted account [username]"` on success. Returns a `404 NOT FOUND` error if the account does not exist, or a `401 FORBIDDEN` error if the password is incorrect.

## Admin Credential Routes

The following examples use `http://example.org` as the URL body. All methods require that the `authorization` header value is a valid JSON Web Token generated by account creation or login. All methods return a `401 FORBIDDEN` error if the JWT has expired, or a `400 BAD REQUEST` error if the JWT is otherwise invalid. All methods also require that the request body is a JSON object, though the permitted values depend on the route; otherwise, a `400 BAD REQUEST` error is returned.

- `POST http://example.org/admin/creds`: Create a new credential pair, with the specified scope values given by the `scopes` property in the request body.
- `PUT http://example.org/admin/creds`: Update an existing credential pair, given by `api-key` and `secret-key` properties in the request body, with the new scopes given by the `scopes` property.
- `GET http://example.org/admin/creds`: Read all credential pairs and their associated scopes for a particular account (denoted by the JWT).
- `DELETE http://example.org/admin/creds`: Delete an existing credential pair, given by the `api-key` and `secret-key` properties in the request body, as well as any associated scopes.