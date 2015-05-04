# --- !Ups

CREATE TABLE IF NOT EXISTS zookeepers (
  id       BIGINT NOT NULL AUTO_INCREMENT,
  port     INT    NOT NULL,
  statusId BIGINT NOT NULL,
  groupId  BIGINT NOT NULL,
  name     VARCHAR(128) CHARACTER SET utf8 NOT NULL,
  host     VARCHAR(128) CHARACTER SET utf8 NOT NULL,
  chroot   VARCHAR(128) CHARACTER SET utf8 NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_name (name)
) ENGINE=MyISAM DEFAULT CHARSET=latin1 COMMENT='zookeepers';

CREATE TABLE IF NOT EXISTS groups (
  id       BIGINT NOT NULL,
  name     VARCHAR(128) CHARACTER SET utf8 NOT NULL,
  PRIMARY KEY (id)
) ENGINE=MyISAM DEFAULT CHARSET=latin1 COMMENT='kafka group, like prod, test, etc.';

CREATE TABLE IF NOT EXISTS status (
  id       BIGINT NOT NULL,
  name     VARCHAR(128) CHARACTER SET utf8 NOT NULL,
  PRIMARY KEY (id)
) ENGINE=MyISAM DEFAULT CHARSET=latin1 COMMENT='zk connect status';


CREATE TABLE IF NOT EXISTS offsetHistory (
  id          BIGINT NOT NULL AUTO_INCREMENT,
  zookeeperId BIGINT NOT NULL,
  topic       VARCHAR(255) CHARACTER SET utf8 NOT NULL,
  PRIMARY KEY (id),
  FOREIGN KEY (zookeeperId) REFERENCES zookeepers(id),
  UNIQUE  KEY uk_zkId_topic (zookeeperId, topic)
) ENGINE=MyISAM DEFAULT CHARSET=latin1 COMMENT='zk and topic relationship';

CREATE TABLE IF NOT EXISTS offsetPoints (
  id              BIGINT NOT NULL AUTO_INCREMENT,
  offsetHistoryId BIGINT NOT NULL,
  partition       INT    NOT NULL,
  offset          BIGINT NOT NULL,
  logSize         BIGINT NOT NULL,
  consumerGroup   VARCHAR(255) CHARACTER SET utf8 NOT NULL,
  timestamp       TIMESTAMP NOT NULL DEFAULT '0000-00-00 00:00:00',
  PRIMARY KEY (id),
  UNIQUE  KEY uk_point (offsetHistoryId, partition, consumerGroup, timestamp),
  FOREIGN KEY (offsetHistoryId) REFERENCES offsetHistory(id),
  KEY idx_for_lag (offsetHistoryId,consumerGroup,timestamp)
) ENGINE=MyISAM DEFAULT CHARSET=latin1 COMMENT='kafka topic records for offset, lag graph';

CREATE TABLE IF NOT EXISTS settings (
  key_     VARCHAR(255) CHARACTER SET utf8 NOT NULL,
  value    VARCHAR(255) CHARACTER SET utf8 NOT NULL,
  PRIMARY KEY (key_)
) ENGINE=MyISAM DEFAULT CHARSET=latin1 COMMENT='settings';

INSERT INTO groups (id, name) VALUES (0, 'ALL');
INSERT INTO groups (id, name) VALUES (1, 'DEVELOPMENT');
INSERT INTO groups (id, name) VALUES (2, 'PRODUCTION');
INSERT INTO groups (id, name) VALUES (3, 'STAGING');
INSERT INTO groups (id, name) VALUES (4, 'TEST');

INSERT INTO status (id, name) VALUES (0, 'CONNECTING');
INSERT INTO status (id, name) VALUES (1, 'CONNECTED');
INSERT INTO status (id, name) VALUES (2, 'DISCONNECTED');
INSERT INTO status (id, name) VALUES (3, 'DELETED');


INSERT INTO settings (key_, value) VALUES ('PURGE_SCHEDULE', '0 0 0 ? * SUN *');
INSERT INTO settings (key_, value) VALUES ('OFFSET_FETCH_INTERVAL', '30');


# --- !Downs

DROP TABLE IF EXISTS zookeepers;
DROP TABLE IF EXISTS groups;
DROP TABLE IF EXISTS status;

DROP TABLE IF EXISTS offsetPoints;
DROP TABLE IF EXISTS offsetHistory;
DROP TABLE IF EXISTS settings;

