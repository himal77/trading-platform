-- One database per service: no shared schema, no cross-service joins.
CREATE DATABASE auth_db   OWNER trading;
CREATE DATABASE wallet_db OWNER trading;
CREATE DATABASE trade_db  OWNER trading;
CREATE DATABASE order_db  OWNER trading;
