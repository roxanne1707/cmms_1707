-- MySQL 8 fresh-install baseline derived from the source-matched isolated PostgreSQL schema.

-- No deployed data is migrated. UTC datetime(6); binary-sensitive identifiers/keys.

-- Original Atlas CMMS attribution and license apply. See mysql-adaptation.md.

CREATE TABLE `additional_cost` (
  `id` BIGINT NOT NULL,
  `created_at` DATETIME(6) NOT NULL,
  `updated_at` DATETIME(6) NOT NULL,
  `created_by` BIGINT NULL,
  `updated_by` BIGINT NULL,
  `cost` DOUBLE NOT NULL,
  `date` DATETIME(6) NULL,
  `description` LONGTEXT NULL,
  `include_to_total_cost` BIT(1) NOT NULL,
  `category_id` BIGINT NULL,
  `assigned_to_id` BIGINT NULL,
  `work_order_id` BIGINT NOT NULL,
  `is_demo` BIT(1) NOT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

CREATE TABLE `api_key` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `label` VARCHAR(255) NOT NULL,
  `code` VARCHAR(500) NOT NULL,
  `user_id` BIGINT NOT NULL,
  `company_id` BIGINT NOT NULL,
  `last_used` DATETIME(6) NULL,
  `created_at` DATETIME(6) NOT NULL,
  `updated_at` DATETIME(6) NOT NULL,
  `created_by` BIGINT NULL,
  `updated_by` BIGINT NULL,
  `expires_at` DATETIME(6) NULL,
  `revoked_at` DATETIME(6) NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

CREATE TABLE `asset` (
  `id` BIGINT NOT NULL,
  `created_at` DATETIME(6) NOT NULL,
  `updated_at` DATETIME(6) NOT NULL,
  `created_by` BIGINT NULL,
  `updated_by` BIGINT NULL,
  `acquisition_cost` DOUBLE NULL,
  `additional_infos` LONGTEXT NULL,
  `archived` BIT(1) NOT NULL,
  `area` VARCHAR(255) NULL,
  `bar_code` VARCHAR(255) NULL,
  `description` LONGTEXT NULL,
  `in_service_date` DATETIME(6) NULL,
  `model` VARCHAR(255) NULL,
  `name` VARCHAR(255) NOT NULL,
  `serial_number` VARCHAR(255) NULL,
  `status` SMALLINT NULL,
  `warranty_expiration_date` DATETIME(6) NULL,
  `company_id` BIGINT NOT NULL,
  `category_id` BIGINT NULL,
  `deprecation_id` BIGINT NULL,
  `image_id` BIGINT NULL,
  `location_id` BIGINT NULL,
  `parent_asset_id` BIGINT NULL,
  `primary_user_id` BIGINT NULL,
  `nfc_id` VARCHAR(255) NULL,
  `power` LONGTEXT NULL,
  `manufacturer` LONGTEXT NULL,
  `custom_id` VARCHAR(255) NULL,
  `is_demo` BIT(1) NOT NULL,
  PRIMARY KEY (`id`),
  CONSTRAINT `uq_asset_company_barcode` UNIQUE (`company_id`,`bar_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

CREATE TABLE `asset_category` (
  `id` BIGINT NOT NULL,
  `created_at` DATETIME(6) NOT NULL,
  `updated_at` DATETIME(6) NOT NULL,
  `created_by` BIGINT NULL,
  `updated_by` BIGINT NULL,
  `name` VARCHAR(255) NOT NULL,
  `company_settings_id` BIGINT NOT NULL,
  `description` LONGTEXT NULL,
  `is_demo` BIT(1) NOT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

CREATE TABLE `asset_downtime` (
  `id` BIGINT NOT NULL,
  `created_at` DATETIME(6) NOT NULL,
  `updated_at` DATETIME(6) NOT NULL,
  `created_by` BIGINT NULL,
  `updated_by` BIGINT NULL,
  `duration` BIGINT NOT NULL,
  `starts_on` DATETIME(6) NULL,
  `company_id` BIGINT NOT NULL,
  `asset_id` BIGINT NOT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

CREATE TABLE `asset_summary_epoch` (
  `company_id` BIGINT NOT NULL,
  `asset_id` BIGINT NOT NULL,
  `epoch` BIGINT NOT NULL DEFAULT 1,
  PRIMARY KEY (`company_id`,`asset_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

CREATE TABLE `auth_session` (
  `id` CHAR(36) NOT NULL,
  `user_id` BIGINT NOT NULL,
  `created_at` DATETIME(6) NOT NULL,
  `last_rotated_at` DATETIME(6) NOT NULL,
  `expires_at` DATETIME(6) NOT NULL,
  `revoked_at` DATETIME(6) NULL,
  `user_agent` VARCHAR(255) NOT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

CREATE TABLE `checklist` (
  `id` BIGINT NOT NULL,
  `created_at` DATETIME(6) NOT NULL,
  `updated_at` DATETIME(6) NOT NULL,
  `category` VARCHAR(255) NULL,
  `description` LONGTEXT NULL,
  `name` VARCHAR(255) NOT NULL,
  `company_settings_id` BIGINT NOT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

CREATE TABLE `checklist_task_bases` (
  `checklist_id` BIGINT NOT NULL,
  `task_bases_id` BIGINT NOT NULL,
  CONSTRAINT `uk_h7shuxfq9i72379fte8od4pdm` UNIQUE (`task_bases_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

CREATE TABLE `comment` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `work_order_id` BIGINT NOT NULL,
  `user_id` BIGINT NOT NULL,
  `content` LONGTEXT NOT NULL,
  `company_id` BIGINT NOT NULL,
  `created_at` DATETIME(6) NOT NULL,
  `updated_at` DATETIME(6) NOT NULL,
  `created_by` BIGINT NULL,
  `updated_by` BIGINT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

CREATE TABLE `comment_files` (
  `comment_id` BIGINT NOT NULL,
  `files_id` BIGINT NOT NULL,
  PRIMARY KEY (`comment_id`,`files_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

CREATE TABLE `company` (
  `id` BIGINT NOT NULL,
  `created_at` DATETIME(6) NOT NULL,
  `updated_at` DATETIME(6) NOT NULL,
  `created_by` BIGINT NULL,
  `updated_by` BIGINT NULL,
  `address` VARCHAR(255) NULL,
  `city` VARCHAR(255) NULL,
  `email` VARCHAR(255) NULL,
  `employees_count` INT NOT NULL,
  `name` VARCHAR(255) NULL,
  `phone` VARCHAR(255) NULL,
  `state` VARCHAR(255) NULL,
  `website` VARCHAR(255) NULL,
  `zip_code` VARCHAR(255) NULL,
  `company_settings_id` BIGINT NULL,
  `logo_id` BIGINT NULL,
  `subscription_id` BIGINT NULL,
  `demo` BIT(1) NULL DEFAULT false,
  `first_work_order_created` BIT(1) NOT NULL DEFAULT false,
  `invited_users` BIT(1) NOT NULL DEFAULT false,
  `imported_assets` BIT(1) NOT NULL DEFAULT false,
  `ldap_domain` VARCHAR(255) NULL,
  CONSTRAINT `company_ldap_domain_key` UNIQUE (`ldap_domain`),
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

CREATE TABLE `company_custom_field_options` (
  `field_id` BIGINT NOT NULL,
  `option_value` VARCHAR(255) NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

CREATE TABLE `company_settings` (
  `id` BIGINT NOT NULL,
  `work_order_request_configuration_id` BIGINT NULL,
  `company_id` BIGINT NULL,
  `general_preferences_id` BIGINT NULL,
  `work_order_configuration_id` BIGINT NULL,
  `ui_configuration_id` BIGINT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

CREATE TABLE `consumer_receipt` (
  `company_id` BIGINT NOT NULL,
  `event_id` CHAR(36) NOT NULL,
  `handler` VARCHAR(80) NOT NULL,
  `completed_at` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  PRIMARY KEY (`company_id`,`event_id`,`handler`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

CREATE TABLE `cost_category` (
  `id` BIGINT NOT NULL,
  `created_at` DATETIME(6) NOT NULL,
  `updated_at` DATETIME(6) NOT NULL,
  `created_by` BIGINT NULL,
  `updated_by` BIGINT NULL,
  `name` VARCHAR(255) NOT NULL,
  `company_settings_id` BIGINT NOT NULL,
  `description` LONGTEXT NULL,
  `is_demo` BIT(1) NOT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

CREATE TABLE `currency` (
  `id` BIGINT NOT NULL,
  `code` VARCHAR(255) NULL,
  `name` VARCHAR(255) NOT NULL,
  PRIMARY KEY (`id`),
  CONSTRAINT `uk_h84pd2rtr12isnifnj655rkra` UNIQUE (`code`),
  CONSTRAINT `uk_ou8q9939fa4k88wjh17qwcmre` UNIQUE (`name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

CREATE TABLE `custom_field` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `label` VARCHAR(255) NOT NULL,
  `field_type` VARCHAR(50) NOT NULL,
  `entity_type` VARCHAR(50) NOT NULL,
  `required` BIT(1) NOT NULL DEFAULT false,
  `copy_on_repeat` BIT(1) NOT NULL DEFAULT false,
  `company_settings_id` BIGINT NOT NULL,
  `created_by` BIGINT NULL,
  `updated_by` BIGINT NULL,
  `created_at` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  `updated_at` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  `field_order` INT NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

CREATE TABLE `custom_field_value` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `custom_field_id` BIGINT NOT NULL,
  `value` LONGTEXT NULL,
  `work_order_id` BIGINT NULL,
  `asset_id` BIGINT NULL,
  `location_id` BIGINT NULL,
  `customer_id` BIGINT NULL,
  `vendor_id` BIGINT NULL,
  `part_id` BIGINT NULL,
  `meter_id` BIGINT NULL,
  `created_by` BIGINT NULL,
  `updated_by` BIGINT NULL,
  `created_at` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  `updated_at` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  `preventive_maintenance_id` BIGINT NULL,
  `request_id` BIGINT NULL,
  `work_order_meter_trigger_id` BIGINT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

CREATE TABLE `custom_sequence` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `company_id` BIGINT NOT NULL,
  `work_order_sequence` BIGINT NOT NULL DEFAULT 1,
  `asset_sequence` BIGINT NOT NULL DEFAULT 1,
  `preventive_maintenance_sequence` BIGINT NOT NULL DEFAULT 1,
  `location_sequence` BIGINT NOT NULL DEFAULT 1,
  `request_sequence` BIGINT NOT NULL DEFAULT 1,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

CREATE TABLE `customer` (
  `id` BIGINT NOT NULL,
  `created_at` DATETIME(6) NOT NULL,
  `updated_at` DATETIME(6) NOT NULL,
  `created_by` BIGINT NULL,
  `updated_by` BIGINT NULL,
  `address` VARCHAR(255) NULL,
  `email` VARCHAR(255) NULL,
  `name` VARCHAR(255) NOT NULL,
  `phone` VARCHAR(255) NULL,
  `website` VARCHAR(255) NULL,
  `billing_address` VARCHAR(255) NULL,
  `billing_address2` VARCHAR(255) NULL,
  `billing_name` VARCHAR(255) NULL,
  `customer_type` VARCHAR(255) NULL,
  `description` LONGTEXT NULL,
  `rate` BIGINT NOT NULL,
  `company_id` BIGINT NOT NULL,
  `billing_currency_id` BIGINT NULL,
  `is_demo` BIT(1) NOT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

CREATE TABLE `deprecation` (
  `id` BIGINT NOT NULL,
  `created_at` DATETIME(6) NOT NULL,
  `updated_at` DATETIME(6) NOT NULL,
  `created_by` BIGINT NULL,
  `updated_by` BIGINT NULL,
  `current_value` BIGINT NOT NULL,
  `purchase_date` DATETIME(6) NULL,
  `purchase_price` BIGINT NOT NULL,
  `rate` INT NOT NULL,
  `residual_value` VARCHAR(255) NULL,
  `usefullife` VARCHAR(255) NULL,
  `company_id` BIGINT NOT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

CREATE TABLE `domain_command` (
  `company_id` BIGINT NOT NULL,
  `namespace` VARCHAR(32) NOT NULL,
  `command_key` VARCHAR(128) NOT NULL,
  `payload_hash` VARCHAR(64) NOT NULL,
  `actor_id` BIGINT NOT NULL,
  `subject_type` VARCHAR(32) NOT NULL,
  `subject_id` BIGINT NOT NULL,
  `response_json` LONGTEXT NOT NULL,
  `created_at` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  PRIMARY KEY (`company_id`,`namespace`,`command_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

CREATE TABLE `domain_event_outbox` (
  `publication_id` CHAR(36) NOT NULL,
  `publish_key` VARCHAR(240) NOT NULL,
  `retry_id` CHAR(36) NOT NULL,
  `company_id` BIGINT NOT NULL,
  `event_id` CHAR(36) NOT NULL,
  `purpose` VARCHAR(20) NOT NULL,
  `dispatch` LONGTEXT NOT NULL,
  `status` VARCHAR(20) NOT NULL DEFAULT 'PENDING',
  `publish_attempt` INT NOT NULL DEFAULT 0,
  `claim_token` CHAR(36) NULL,
  `lease_until` DATETIME(6) NULL,
  `next_attempt_at` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  `last_error` VARCHAR(200) NULL,
  `created_at` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  `published_at` DATETIME(6) NULL,
  PRIMARY KEY (`publication_id`),
  CONSTRAINT `domain_event_outbox_publish_key_key` UNIQUE (`publish_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

CREATE TABLE `event_delivery` (
  `id` CHAR(36) NOT NULL,
  `company_id` BIGINT NOT NULL,
  `event_id` CHAR(36) NOT NULL,
  `channel` VARCHAR(24) NOT NULL,
  `destination_id` VARCHAR(160) NOT NULL,
  `purpose` VARCHAR(80) NOT NULL,
  `payload` LONGTEXT NOT NULL,
  `state` VARCHAR(20) NOT NULL DEFAULT 'READY',
  `attempt` INT NOT NULL DEFAULT 0,
  `replay_generation` INT NOT NULL DEFAULT 0,
  `claim_token` CHAR(36) NULL,
  `lease_until` DATETIME(6) NULL,
  `next_attempt_at` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  `last_error` VARCHAR(200) NULL,
  `created_at` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  `sent_at` DATETIME(6) NULL,
  CONSTRAINT `event_delivery_company_id_event_id_channel_destination_id_p_key` UNIQUE (`company_id`,`event_id`,`channel`,`destination_id`,`purpose`),
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

CREATE TABLE `event_quarantine` (
  `fingerprint` VARCHAR(64) NOT NULL,
  `reason` VARCHAR(200) NOT NULL,
  `first_seen_at` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  `last_seen_at` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  `occurrences` BIGINT NOT NULL DEFAULT 1,
  PRIMARY KEY (`fingerprint`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

CREATE TABLE `event_replay_audit` (
  `id` CHAR(36) NOT NULL,
  `company_id` BIGINT NOT NULL,
  `actor_id` BIGINT NULL,
  `resource_id` CHAR(36) NOT NULL,
  `resource_type` VARCHAR(20) NOT NULL,
  `reason` VARCHAR(500) NOT NULL,
  `created_at` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

CREATE TABLE `event_report_attachment` (
  `company_id` BIGINT NOT NULL,
  `event_id` CHAR(36) NOT NULL,
  `filename` VARCHAR(180) NOT NULL,
  `content` LONGBLOB NOT NULL,
  `created_at` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  PRIMARY KEY (`company_id`,`event_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

CREATE TABLE `event_retry` (
  `id` CHAR(36) NOT NULL,
  `company_id` BIGINT NOT NULL,
  `event_id` CHAR(36) NOT NULL,
  `handler` VARCHAR(80) NOT NULL,
  `replay_generation` INT NOT NULL DEFAULT 0,
  `business_attempt` INT NOT NULL DEFAULT 0,
  `state` VARCHAR(30) NOT NULL DEFAULT 'READY',
  `envelope` LONGTEXT NOT NULL,
  `claim_token` CHAR(36) NULL,
  `lease_until` DATETIME(6) NULL,
  `next_attempt_at` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  `last_error` VARCHAR(200) NULL,
  `created_at` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  `updated_at` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  CONSTRAINT `event_retry_company_id_event_id_handler_replay_generation_b_key` UNIQUE (`company_id`,`event_id`,`handler`,`replay_generation`,`business_attempt`),
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

CREATE TABLE `field_configuration` (
  `id` BIGINT NOT NULL,
  `field_name` VARCHAR(255) NOT NULL,
  `field_type` SMALLINT NULL,
  `work_order_configuration_id` BIGINT NULL,
  `work_order_request_configuration_id` BIGINT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

CREATE TABLE `file` (
  `id` BIGINT NOT NULL,
  `created_at` DATETIME(6) NOT NULL,
  `updated_at` DATETIME(6) NOT NULL,
  `created_by` BIGINT NULL,
  `updated_by` BIGINT NULL,
  `name` VARCHAR(255) NOT NULL,
  `type` SMALLINT NULL,
  `path` LONGTEXT NOT NULL,
  `company_id` BIGINT NOT NULL,
  `task_id` BIGINT NULL,
  `hidden` BIT(1) NOT NULL,
  `thumbnail_path` VARCHAR(255) NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

CREATE TABLE `floor_plan` (
  `id` BIGINT NOT NULL,
  `area` BIGINT NOT NULL,
  `name` VARCHAR(255) NOT NULL,
  `image_id` BIGINT NULL,
  `location_id` BIGINT NOT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

CREATE TABLE `general_preferences` (
  `id` BIGINT NOT NULL,
  `ask_feed_back_onwoclosed` BIT(1) NOT NULL,
  `auto_assign_requests` BIT(1) NOT NULL,
  `auto_assign_work_orders` BIT(1) NOT NULL,
  `business_type` SMALLINT NULL,
  `date_format` SMALLINT NULL,
  `disable_closed_work_orders_notif` BIT(1) NOT NULL,
  `labor_cost_in_total_cost` BIT(1) NOT NULL,
  `language` SMALLINT NULL,
  `time_zone` VARCHAR(255) NOT NULL,
  `wo_update_for_requesters` BIT(1) NOT NULL,
  `company_settings_id` BIGINT NULL,
  `currency_id` BIGINT NULL,
  `simplified_work_order` BIT(1) NOT NULL DEFAULT false,
  `days_before_prev_maint_notification` INT NOT NULL DEFAULT 1,
  `csv_separator` VARCHAR(1) NULL DEFAULT ',',
  `auto_generate_asset_barcode` BIT(1) NULL,
  `color` LONGTEXT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

CREATE TABLE `keygen_request_tracker` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `request_date` DATE NOT NULL,
  `request_count` INT NOT NULL,
  PRIMARY KEY (`id`),
  CONSTRAINT `keygen_request_tracker_request_date_key` UNIQUE (`request_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

CREATE TABLE `labor` (
  `id` BIGINT NOT NULL,
  `created_at` DATETIME(6) NOT NULL,
  `updated_at` DATETIME(6) NOT NULL,
  `created_by` BIGINT NULL,
  `updated_by` BIGINT NULL,
  `duration` BIGINT NOT NULL,
  `hourly_rate` BIGINT NOT NULL,
  `include_to_total_time` BIT(1) NOT NULL,
  `logged` BIT(1) NOT NULL,
  `started_at` DATETIME(6) NULL,
  `status` SMALLINT NULL,
  `company_id` BIGINT NOT NULL,
  `assigned_to_id` BIGINT NULL,
  `time_category_id` BIGINT NULL,
  `work_order_id` BIGINT NOT NULL,
  `is_demo` BIT(1) NOT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

CREATE TABLE `location` (
  `id` BIGINT NOT NULL,
  `created_at` DATETIME(6) NOT NULL,
  `updated_at` DATETIME(6) NOT NULL,
  `created_by` BIGINT NULL,
  `updated_by` BIGINT NULL,
  `address` VARCHAR(255) NULL,
  `latitude` DOUBLE NULL,
  `longitude` DOUBLE NULL,
  `name` VARCHAR(255) NOT NULL,
  `company_id` BIGINT NOT NULL,
  `image_id` BIGINT NULL,
  `parent_location_id` BIGINT NULL,
  `custom_id` VARCHAR(255) NULL,
  `is_demo` BIT(1) NOT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

CREATE TABLE `maintenance_schedule_execution` (
  `company_id` BIGINT NOT NULL,
  `schedule_id` BIGINT NOT NULL,
  `execution_kind` VARCHAR(20) NOT NULL,
  `scheduled_at` DATETIME(6) NOT NULL,
  `work_order_id` BIGINT NULL,
  `created_at` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  PRIMARY KEY (`company_id`,`schedule_id`,`execution_kind`,`scheduled_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

CREATE TABLE `maintenance_sla_case` (
  `work_order_id` BIGINT NOT NULL,
  `company_id` BIGINT NOT NULL,
  `priority` INT NOT NULL,
  `started_at` DATETIME(6) NOT NULL,
  `response_due_at` DATETIME(6) NOT NULL,
  `repair_due_at` DATETIME(6) NOT NULL,
  `responded_at` DATETIME(6) NULL,
  `completed_at` DATETIME(6) NULL,
  `response_event_id` CHAR(36) NULL,
  `repair_event_id` CHAR(36) NULL,
  `enrolled_by` BIGINT NULL,
  `enrollment_reason` VARCHAR(500) NOT NULL,
  `created_at` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  PRIMARY KEY (`work_order_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

CREATE TABLE `maintenance_sla_escalation` (
  `company_id` BIGINT NOT NULL,
  `work_order_id` BIGINT NOT NULL,
  `kind` VARCHAR(20) NOT NULL,
  `event_id` CHAR(36) NOT NULL,
  `occurred_at` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  CONSTRAINT `maintenance_sla_escalation_event_id_key` UNIQUE (`event_id`),
  PRIMARY KEY (`work_order_id`,`kind`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

CREATE TABLE `maintenance_sla_policy` (
  `company_id` BIGINT NOT NULL,
  `priority` INT NOT NULL,
  `response_minutes` INT NOT NULL,
  `repair_minutes` INT NOT NULL,
  `enabled` BIT(1) NOT NULL DEFAULT true,
  `effective_from` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  `updated_at` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  `updated_by` BIGINT NOT NULL,
  PRIMARY KEY (`company_id`,`priority`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

CREATE TABLE `meter` (
  `id` BIGINT NOT NULL,
  `created_at` DATETIME(6) NOT NULL,
  `updated_at` DATETIME(6) NOT NULL,
  `created_by` BIGINT NULL,
  `updated_by` BIGINT NULL,
  `name` VARCHAR(255) NOT NULL,
  `unit` VARCHAR(255) NULL,
  `update_frequency` INT NOT NULL,
  `company_id` BIGINT NOT NULL,
  `asset_id` BIGINT NOT NULL,
  `image_id` BIGINT NULL,
  `location_id` BIGINT NULL,
  `meter_category_id` BIGINT NULL,
  `is_demo` BIT(1) NOT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

CREATE TABLE `meter_category` (
  `id` BIGINT NOT NULL,
  `created_at` DATETIME(6) NOT NULL,
  `updated_at` DATETIME(6) NOT NULL,
  `created_by` BIGINT NULL,
  `updated_by` BIGINT NULL,
  `name` VARCHAR(255) NOT NULL,
  `company_settings_id` BIGINT NOT NULL,
  `description` LONGTEXT NULL,
  `is_demo` BIT(1) NOT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

CREATE TABLE `meter_trigger_execution` (
  `company_id` BIGINT NOT NULL,
  `trigger_id` BIGINT NOT NULL,
  `episode` BIGINT NOT NULL,
  `reading_id` BIGINT NULL,
  `work_order_id` BIGINT NULL,
  `rule_hash` VARCHAR(64) NOT NULL,
  `created_at` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  PRIMARY KEY (`trigger_id`,`episode`),
  CONSTRAINT `meter_trigger_execution_work_order_id_key` UNIQUE (`work_order_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

CREATE TABLE `meter_trigger_state` (
  `trigger_id` BIGINT NOT NULL,
  `company_id` BIGINT NOT NULL,
  `abnormal` BIT(1) NOT NULL DEFAULT false,
  `opened` BIT(1) NOT NULL DEFAULT false,
  `episode` BIGINT NOT NULL DEFAULT 0,
  `opened_count` BIGINT NOT NULL DEFAULT 0,
  `first_abnormal_at` DATETIME(6) NULL,
  `rule_hash` VARCHAR(64) NULL,
  `last_reading_id` BIGINT NULL,
  PRIMARY KEY (`trigger_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

CREATE TABLE `multi_parts` (
  `id` BIGINT NOT NULL,
  `created_at` DATETIME(6) NOT NULL,
  `updated_at` DATETIME(6) NOT NULL,
  `created_by` BIGINT NULL,
  `updated_by` BIGINT NULL,
  `name` VARCHAR(255) NOT NULL,
  `company_id` BIGINT NOT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

CREATE TABLE `notification` (
  `id` BIGINT NOT NULL,
  `created_at` DATETIME(6) NOT NULL,
  `updated_at` DATETIME(6) NOT NULL,
  `created_by` BIGINT NULL,
  `updated_by` BIGINT NULL,
  `message` VARCHAR(255) NOT NULL,
  `notification_type` SMALLINT NULL,
  `resource_id` BIGINT NULL,
  `seen` BIT(1) NOT NULL,
  `user_id` BIGINT NULL,
  `event_id` CHAR(36) NULL,
  `event_company_id` BIGINT NULL,
  `event_purpose` VARCHAR(80) NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

CREATE TABLE `own_user` (
  `id` BIGINT NOT NULL,
  `created_at` DATETIME(6) NOT NULL,
  `updated_at` DATETIME(6) NOT NULL,
  `created_by` BIGINT NULL,
  `updated_by` BIGINT NULL,
  `email` VARCHAR(255) NOT NULL,
  `enabled` BIT(1) NOT NULL,
  `first_name` VARCHAR(255) NOT NULL,
  `job_title` VARCHAR(255) NULL,
  `last_name` VARCHAR(255) NOT NULL,
  `owns_company` BIT(1) NOT NULL,
  `password` VARCHAR(255) NOT NULL,
  `phone` VARCHAR(255) NULL,
  `rate` BIGINT NOT NULL,
  `username` VARCHAR(255) NOT NULL,
  `company_id` BIGINT NULL,
  `image_id` BIGINT NULL,
  `location_id` BIGINT NULL,
  `role_id` BIGINT NOT NULL,
  `user_settings_id` BIGINT NULL,
  `enabled_in_subscription` BIT(1) NOT NULL,
  `last_login` DATETIME(6) NULL,
  `created_via_sso` BIT(1) NOT NULL DEFAULT false,
  `sso_provider` VARCHAR(255) NULL,
  `sso_provider_id` VARCHAR(255) NULL,
  `gclid` LONGTEXT NULL,
  `utm_source` VARCHAR(255) NULL,
  `utm_medium` VARCHAR(255) NULL,
  `utm_campaign` VARCHAR(255) NULL,
  `utm_term` VARCHAR(255) NULL,
  `utm_content` VARCHAR(255) NULL,
  `fbclid` VARCHAR(255) NULL,
  `referrer` LONGTEXT NULL,
  `paddle_user_id` LONGTEXT NULL,
  `app_stats_id` BIGINT NULL,
  `shift_configuration_id` BIGINT NULL,
  `session_invalidated_at` DATETIME(6) NULL,
  `language` INT NULL,
  CONSTRAINT `own_user_app_stats_id_key` UNIQUE (`app_stats_id`),
  PRIMARY KEY (`id`),
  CONSTRAINT `uk_hw660abbm8lkw2m1ty1hhlkd7` UNIQUE (`email`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

CREATE TABLE `own_user_preventive_maintenances` (
  `own_user_id` BIGINT NOT NULL,
  `preventive_maintenances_id` BIGINT NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

CREATE TABLE `own_user_work_orders` (
  `own_user_id` BIGINT NOT NULL,
  `work_orders_id` BIGINT NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

CREATE TABLE `part` (
  `id` BIGINT NOT NULL,
  `created_at` DATETIME(6) NOT NULL,
  `updated_at` DATETIME(6) NOT NULL,
  `created_by` BIGINT NULL,
  `updated_by` BIGINT NULL,
  `additional_infos` LONGTEXT NULL,
  `area` VARCHAR(255) NULL,
  `barcode` VARCHAR(255) NULL,
  `category_id` BIGINT NULL,
  `cost` DOUBLE NOT NULL,
  `description` LONGTEXT NULL,
  `min_quantity` DOUBLE NOT NULL,
  `name` VARCHAR(255) NOT NULL,
  `non_stock` BIT(1) NOT NULL,
  `quantity` DOUBLE NOT NULL,
  `company_id` BIGINT NOT NULL,
  `image_id` BIGINT NULL,
  `unit` LONGTEXT NULL,
  `is_demo` BIT(1) NOT NULL,
  `version` BIGINT NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  CONSTRAINT `uq_part_company_barcode` UNIQUE (`company_id`,`barcode`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

CREATE TABLE `part_category` (
  `id` BIGINT NOT NULL,
  `created_at` DATETIME(6) NOT NULL,
  `updated_at` DATETIME(6) NOT NULL,
  `created_by` BIGINT NULL,
  `updated_by` BIGINT NULL,
  `name` VARCHAR(255) NOT NULL,
  `company_settings_id` BIGINT NOT NULL,
  `description` LONGTEXT NULL,
  `is_demo` BIT(1) NOT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

CREATE TABLE `part_preventive_maintenances` (
  `part_id` BIGINT NOT NULL,
  `preventive_maintenances_id` BIGINT NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

CREATE TABLE `part_quantity` (
  `id` BIGINT NOT NULL,
  `created_at` DATETIME(6) NOT NULL,
  `updated_at` DATETIME(6) NOT NULL,
  `created_by` BIGINT NULL,
  `updated_by` BIGINT NULL,
  `quantity` DOUBLE NOT NULL,
  `company_id` BIGINT NOT NULL,
  `part_id` BIGINT NOT NULL,
  `purchase_order_id` BIGINT NULL,
  `work_order_id` BIGINT NULL,
  `is_demo` BIT(1) NOT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

CREATE TABLE `part_transaction` (
  `id` BIGINT NOT NULL,
  `created_at` DATETIME(6) NOT NULL,
  `updated_at` DATETIME(6) NOT NULL,
  `created_by` BIGINT NULL,
  `updated_by` BIGINT NULL,
  `quantity` DOUBLE NOT NULL,
  `company_id` BIGINT NOT NULL,
  `part_id` BIGINT NOT NULL,
  `work_order_id` BIGINT NULL,
  `description` LONGTEXT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

CREATE TABLE `preventive_maintenance` (
  `id` BIGINT NOT NULL,
  `created_at` DATETIME(6) NOT NULL,
  `updated_at` DATETIME(6) NOT NULL,
  `created_by` BIGINT NULL,
  `updated_by` BIGINT NULL,
  `description` LONGTEXT NULL,
  `due_date` DATETIME(6) NULL,
  `estimated_duration` DOUBLE NOT NULL,
  `priority` SMALLINT NULL,
  `required_signature` BIT(1) NOT NULL,
  `title` VARCHAR(255) NOT NULL,
  `name` VARCHAR(255) NULL,
  `company_id` BIGINT NOT NULL,
  `asset_id` BIGINT NULL,
  `category_id` BIGINT NULL,
  `image_id` BIGINT NULL,
  `location_id` BIGINT NULL,
  `primary_user_id` BIGINT NULL,
  `team_id` BIGINT NULL,
  `schedule_id` BIGINT NULL,
  `estimated_start_date` DATETIME(6) NULL,
  `custom_id` VARCHAR(255) NULL,
  `is_demo` BIT(1) NOT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

CREATE TABLE `preventive_maintenance_assigned_to` (
  `preventive_maintenance_id` BIGINT NOT NULL,
  `assigned_to_id` BIGINT NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

CREATE TABLE `preventive_maintenance_customers` (
  `preventive_maintenance_id` BIGINT NOT NULL,
  `customers_id` BIGINT NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

CREATE TABLE `preventive_maintenance_files` (
  `preventive_maintenance_id` BIGINT NOT NULL,
  `files_id` BIGINT NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

CREATE TABLE `purchase_order` (
  `id` BIGINT NOT NULL,
  `created_at` DATETIME(6) NOT NULL,
  `updated_at` DATETIME(6) NOT NULL,
  `created_by` BIGINT NULL,
  `updated_by` BIGINT NULL,
  `additional_info_date` DATETIME(6) NULL,
  `additional_info_notes` VARCHAR(255) NULL,
  `additional_info_requisitioned_name` VARCHAR(255) NULL,
  `additional_info_shipping_order_category` VARCHAR(255) NULL,
  `additional_info_term` VARCHAR(255) NULL,
  `name` VARCHAR(255) NOT NULL,
  `shipping_additional_detail` VARCHAR(255) NULL,
  `shipping_address` VARCHAR(255) NULL,
  `shipping_city` VARCHAR(255) NULL,
  `shipping_company_name` VARCHAR(255) NULL,
  `shipping_due_date` DATETIME(6) NULL,
  `shipping_fax` VARCHAR(255) NULL,
  `shipping_phone` VARCHAR(255) NULL,
  `shipping_ship_to_name` VARCHAR(255) NULL,
  `shipping_state` VARCHAR(255) NULL,
  `shipping_zip_code` VARCHAR(255) NULL,
  `status` SMALLINT NULL,
  `company_id` BIGINT NOT NULL,
  `category_id` BIGINT NULL,
  `vendor_id` BIGINT NULL,
  `is_demo` BIT(1) NOT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

CREATE TABLE `purchase_order_category` (
  `id` BIGINT NOT NULL,
  `created_at` DATETIME(6) NOT NULL,
  `updated_at` DATETIME(6) NOT NULL,
  `created_by` BIGINT NULL,
  `updated_by` BIGINT NULL,
  `name` VARCHAR(255) NOT NULL,
  `company_settings_id` BIGINT NOT NULL,
  `description` LONGTEXT NULL,
  `is_demo` BIT(1) NOT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

CREATE TABLE `push_notification_token` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `token` VARCHAR(255) NULL,
  `user_id` BIGINT NOT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

CREATE TABLE `QRTZ_BLOB_TRIGGERS` (
  `sched_name` VARCHAR(120) NOT NULL,
  `trigger_name` VARCHAR(200) NOT NULL,
  `trigger_group` VARCHAR(200) NOT NULL,
  `blob_data` LONGBLOB NULL,
  PRIMARY KEY (`sched_name`,`trigger_name`,`trigger_group`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

CREATE TABLE `QRTZ_CALENDARS` (
  `sched_name` VARCHAR(120) NOT NULL,
  `calendar_name` VARCHAR(200) NOT NULL,
  `calendar` LONGBLOB NOT NULL,
  PRIMARY KEY (`sched_name`,`calendar_name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

CREATE TABLE `QRTZ_CRON_TRIGGERS` (
  `sched_name` VARCHAR(120) NOT NULL,
  `trigger_name` VARCHAR(200) NOT NULL,
  `trigger_group` VARCHAR(200) NOT NULL,
  `cron_expression` VARCHAR(120) NOT NULL,
  `time_zone_id` VARCHAR(80) NULL,
  PRIMARY KEY (`sched_name`,`trigger_name`,`trigger_group`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

CREATE TABLE `QRTZ_FIRED_TRIGGERS` (
  `sched_name` VARCHAR(120) NOT NULL,
  `entry_id` VARCHAR(95) NOT NULL,
  `trigger_name` VARCHAR(200) NOT NULL,
  `trigger_group` VARCHAR(200) NOT NULL,
  `instance_name` VARCHAR(200) NOT NULL,
  `fired_time` BIGINT NOT NULL,
  `sched_time` BIGINT NOT NULL,
  `priority` INT NOT NULL,
  `state` VARCHAR(16) NOT NULL,
  `job_name` VARCHAR(200) NULL,
  `job_group` VARCHAR(200) NULL,
  `is_nonconcurrent` BIT(1) NULL,
  `requests_recovery` BIT(1) NULL,
  PRIMARY KEY (`sched_name`,`entry_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

CREATE TABLE `QRTZ_JOB_DETAILS` (
  `sched_name` VARCHAR(120) NOT NULL,
  `job_name` VARCHAR(200) NOT NULL,
  `job_group` VARCHAR(200) NOT NULL,
  `description` VARCHAR(250) NULL,
  `job_class_name` VARCHAR(250) NOT NULL,
  `is_durable` BIT(1) NOT NULL,
  `is_nonconcurrent` BIT(1) NOT NULL,
  `is_update_data` BIT(1) NOT NULL,
  `requests_recovery` BIT(1) NOT NULL,
  `job_data` LONGBLOB NULL,
  PRIMARY KEY (`sched_name`,`job_name`,`job_group`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

CREATE TABLE `QRTZ_LOCKS` (
  `sched_name` VARCHAR(120) NOT NULL,
  `lock_name` VARCHAR(40) NOT NULL,
  PRIMARY KEY (`sched_name`,`lock_name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

CREATE TABLE `QRTZ_PAUSED_TRIGGER_GRPS` (
  `sched_name` VARCHAR(120) NOT NULL,
  `trigger_group` VARCHAR(200) NOT NULL,
  PRIMARY KEY (`sched_name`,`trigger_group`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

CREATE TABLE `QRTZ_SCHEDULER_STATE` (
  `sched_name` VARCHAR(120) NOT NULL,
  `instance_name` VARCHAR(200) NOT NULL,
  `last_checkin_time` BIGINT NOT NULL,
  `checkin_interval` BIGINT NOT NULL,
  PRIMARY KEY (`sched_name`,`instance_name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

CREATE TABLE `QRTZ_SIMPLE_TRIGGERS` (
  `sched_name` VARCHAR(120) NOT NULL,
  `trigger_name` VARCHAR(200) NOT NULL,
  `trigger_group` VARCHAR(200) NOT NULL,
  `repeat_count` BIGINT NOT NULL,
  `repeat_interval` BIGINT NOT NULL,
  `times_triggered` BIGINT NOT NULL,
  PRIMARY KEY (`sched_name`,`trigger_name`,`trigger_group`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

CREATE TABLE `QRTZ_SIMPROP_TRIGGERS` (
  `sched_name` VARCHAR(120) NOT NULL,
  `trigger_name` VARCHAR(200) NOT NULL,
  `trigger_group` VARCHAR(200) NOT NULL,
  `str_prop_1` VARCHAR(512) NULL,
  `str_prop_2` VARCHAR(512) NULL,
  `str_prop_3` VARCHAR(512) NULL,
  `int_prop_1` INT NULL,
  `int_prop_2` INT NULL,
  `long_prop_1` BIGINT NULL,
  `long_prop_2` BIGINT NULL,
  `dec_prop_1` DECIMAL(13,4) NULL,
  `dec_prop_2` DECIMAL(13,4) NULL,
  `bool_prop_1` BIT(1) NULL,
  `bool_prop_2` BIT(1) NULL,
  PRIMARY KEY (`sched_name`,`trigger_name`,`trigger_group`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

CREATE TABLE `QRTZ_TRIGGERS` (
  `sched_name` VARCHAR(120) NOT NULL,
  `trigger_name` VARCHAR(200) NOT NULL,
  `trigger_group` VARCHAR(200) NOT NULL,
  `job_name` VARCHAR(200) NOT NULL,
  `job_group` VARCHAR(200) NOT NULL,
  `description` VARCHAR(250) NULL,
  `next_fire_time` BIGINT NULL,
  `prev_fire_time` BIGINT NULL,
  `priority` INT NULL,
  `trigger_state` VARCHAR(16) NOT NULL,
  `trigger_type` VARCHAR(8) NOT NULL,
  `start_time` BIGINT NOT NULL,
  `end_time` BIGINT NULL,
  `calendar_name` VARCHAR(200) NULL,
  `misfire_instr` SMALLINT NULL,
  `job_data` LONGBLOB NULL,
  PRIMARY KEY (`sched_name`,`trigger_name`,`trigger_group`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

CREATE TABLE `reading` (
  `id` BIGINT NOT NULL,
  `created_at` DATETIME(6) NOT NULL,
  `updated_at` DATETIME(6) NOT NULL,
  `created_by` BIGINT NULL,
  `updated_by` BIGINT NULL,
  `value` DOUBLE NOT NULL,
  `meter_id` BIGINT NOT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

CREATE TABLE `refresh_token` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `token_hash` VARCHAR(255) NOT NULL,
  `user_id` BIGINT NOT NULL,
  `expires_at` DATETIME(6) NULL,
  `created_at` DATETIME(6) NULL,
  `revoked` BIT(1) NULL,
  `replaced_by_token_hash` VARCHAR(255) NULL,
  `session_id` CHAR(36) NULL,
  PRIMARY KEY (`id`),
  CONSTRAINT `refresh_token_token_hash_key` UNIQUE (`token_hash`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

CREATE TABLE `relation` (
  `id` BIGINT NOT NULL,
  `created_at` DATETIME(6) NOT NULL,
  `updated_at` DATETIME(6) NOT NULL,
  `created_by` BIGINT NULL,
  `updated_by` BIGINT NULL,
  `relation_type` SMALLINT NOT NULL,
  `company_id` BIGINT NOT NULL,
  `child_id` BIGINT NOT NULL,
  `parent_id` BIGINT NOT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

CREATE TABLE `request` (
  `id` BIGINT NOT NULL,
  `created_at` DATETIME(6) NOT NULL,
  `updated_at` DATETIME(6) NOT NULL,
  `created_by` BIGINT NULL,
  `updated_by` BIGINT NULL,
  `description` LONGTEXT NULL,
  `due_date` DATETIME(6) NULL,
  `estimated_duration` DOUBLE NOT NULL,
  `priority` SMALLINT NULL,
  `required_signature` BIT(1) NOT NULL,
  `title` VARCHAR(255) NOT NULL,
  `cancelled` BIT(1) NOT NULL,
  `company_id` BIGINT NOT NULL,
  `asset_id` BIGINT NULL,
  `category_id` BIGINT NULL,
  `image_id` BIGINT NULL,
  `location_id` BIGINT NULL,
  `primary_user_id` BIGINT NULL,
  `team_id` BIGINT NULL,
  `work_order_id` BIGINT NULL,
  `cancellation_reason` LONGTEXT NULL,
  `estimated_start_date` DATETIME(6) NULL,
  `audio_description_id` BIGINT NULL,
  `custom_id` VARCHAR(255) NULL,
  `is_demo` BIT(1) NOT NULL,
  `request_portal_id` BIGINT NULL,
  `contact` LONGTEXT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

CREATE TABLE `request_assigned_to` (
  `request_id` BIGINT NOT NULL,
  `assigned_to_id` BIGINT NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

CREATE TABLE `request_customers` (
  `request_id` BIGINT NOT NULL,
  `customers_id` BIGINT NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

CREATE TABLE `request_files` (
  `request_id` BIGINT NOT NULL,
  `files_id` BIGINT NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

CREATE TABLE `request_link_audit` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `request_id` BIGINT NOT NULL,
  `company_id` BIGINT NOT NULL,
  `work_order_id` BIGINT NOT NULL,
  `actor_id` BIGINT NOT NULL,
  `reason` VARCHAR(500) NOT NULL,
  `linked_at` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  PRIMARY KEY (`id`),
  CONSTRAINT `request_link_audit_request_id_work_order_id_key` UNIQUE (`request_id`,`work_order_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

CREATE TABLE `request_portal` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `company_id` BIGINT NOT NULL,
  `title` VARCHAR(255) NOT NULL,
  `welcome_message` LONGTEXT NULL,
  `uuid` VARCHAR(255) NOT NULL,
  `created_at` DATETIME(6) NOT NULL,
  `updated_at` DATETIME(6) NOT NULL,
  `created_by` BIGINT NULL,
  `updated_by` BIGINT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

CREATE TABLE `request_portal_field` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `company_id` BIGINT NOT NULL,
  `type` VARCHAR(50) NOT NULL,
  `location_id` BIGINT NULL,
  `asset_id` BIGINT NULL,
  `required` BIT(1) NOT NULL DEFAULT false,
  `request_portal_id` BIGINT NOT NULL,
  `created_at` DATETIME(6) NOT NULL,
  `updated_at` DATETIME(6) NOT NULL,
  `created_by` BIGINT NULL,
  `updated_by` BIGINT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

CREATE TABLE `revinfo` (
  `rev` INT NOT NULL AUTO_INCREMENT,
  `revtstmp` BIGINT NOT NULL,
  `user_id` BIGINT NULL,
  PRIMARY KEY (`rev`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

CREATE TABLE `role` (
  `id` BIGINT NOT NULL,
  `code` SMALLINT NULL,
  `description` LONGTEXT NULL,
  `external_id` VARCHAR(255) NULL,
  `name` VARCHAR(255) NOT NULL,
  `paid` BIT(1) NOT NULL,
  `role_type` SMALLINT NOT NULL,
  `company_settings_id` BIGINT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

CREATE TABLE `role_create_permissions` (
  `role_id` BIGINT NOT NULL,
  `create_permissions` SMALLINT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

CREATE TABLE `role_delete_other_permissions` (
  `role_id` BIGINT NOT NULL,
  `delete_other_permissions` SMALLINT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

CREATE TABLE `role_edit_other_permissions` (
  `role_id` BIGINT NOT NULL,
  `edit_other_permissions` SMALLINT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

CREATE TABLE `role_view_other_permissions` (
  `role_id` BIGINT NOT NULL,
  `view_other_permissions` SMALLINT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

CREATE TABLE `role_view_permissions` (
  `role_id` BIGINT NOT NULL,
  `view_permissions` SMALLINT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

CREATE TABLE `schedule` (
  `id` BIGINT NOT NULL,
  `created_at` DATETIME(6) NOT NULL,
  `updated_at` DATETIME(6) NOT NULL,
  `created_by` BIGINT NULL,
  `updated_by` BIGINT NULL,
  `disabled` BIT(1) NOT NULL,
  `ends_on` DATETIME(6) NULL,
  `frequency` INT NOT NULL,
  `starts_on` DATETIME(6) NOT NULL,
  `preventive_maintenance_id` BIGINT NULL,
  `due_date_delay` INT NULL,
  `recurrence_type` VARCHAR(255) NOT NULL,
  `recurrence_based_on` VARCHAR(255) NOT NULL,
  `is_demo` BIT(1) NOT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

CREATE TABLE `schedule_days_of_week` (
  `schedule_id` BIGINT NOT NULL,
  `days_of_week` INT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

CREATE TABLE `shift_configuration` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `created_at` DATETIME(6) NOT NULL,
  `updated_at` DATETIME(6) NOT NULL,
  `created_by` BIGINT NULL,
  `updated_by` BIGINT NULL,
  `enabled` BIT(1) NOT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

CREATE TABLE `shift_configuration_days` (
  `shift_configuration_id` BIGINT NOT NULL,
  `day_of_week` VARCHAR(20) NOT NULL,
  `availability_minutes` INT NOT NULL,
  `enabled` BIT(1) NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

CREATE TABLE `shift_configuration_exceptions` (
  `shift_configuration_id` BIGINT NOT NULL,
  `exception_date` DATE NOT NULL,
  `availability_minutes` INT NOT NULL,
  `enabled` BIT(1) NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

CREATE TABLE `subscription` (
  `id` BIGINT NOT NULL,
  `created_at` DATETIME(6) NOT NULL,
  `updated_at` DATETIME(6) NOT NULL,
  `created_by` BIGINT NULL,
  `updated_by` BIGINT NULL,
  `activated` BIT(1) NOT NULL,
  `ends_on` DATETIME(6) NULL,
  `monthly` BIT(1) NOT NULL,
  `starts_on` DATETIME(6) NULL,
  `users_count` INT NOT NULL,
  `subscription_plan_id` BIGINT NOT NULL,
  `downgrade_needed` BIT(1) NOT NULL,
  `upgrade_needed` BIT(1) NOT NULL,
  `paddle_subscription_id` VARCHAR(255) NULL,
  `scheduled_change_date` DATETIME(6) NULL,
  `scheduled_change_type` SMALLINT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

CREATE TABLE `subscription_change_request` (
  `id` BIGINT NOT NULL,
  `created_at` DATETIME(6) NOT NULL,
  `updated_at` DATETIME(6) NOT NULL,
  `created_by` BIGINT NULL,
  `updated_by` BIGINT NULL,
  `code` VARCHAR(255) NOT NULL,
  `monthly` BIT(1) NOT NULL,
  `company_id` BIGINT NOT NULL,
  `users_count` INT NOT NULL DEFAULT 3,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

CREATE TABLE `subscription_plan` (
  `id` BIGINT NOT NULL,
  `code` VARCHAR(255) NULL,
  `monthly_cost_per_user` DOUBLE NOT NULL,
  `name` VARCHAR(255) NOT NULL,
  `yearly_cost_per_user` DOUBLE NOT NULL,
  `monthly_paddle_price_id` LONGTEXT NULL,
  `yearly_paddle_price_id` LONGTEXT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

CREATE TABLE `subscription_plan_features` (
  `subscription_plan_id` BIGINT NOT NULL,
  `features` SMALLINT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

CREATE TABLE `super_account_relation` (
  `id` BIGINT NOT NULL,
  `super_user_id` BIGINT NOT NULL,
  `child_user_id` BIGINT NOT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

CREATE TABLE `t_asset_customer_associations` (
  `id_customer` BIGINT NOT NULL,
  `id_asset` BIGINT NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

CREATE TABLE `t_asset_file_associations` (
  `id_asset` BIGINT NOT NULL,
  `id_file` BIGINT NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

CREATE TABLE `t_asset_part_associations` (
  `id_part` BIGINT NOT NULL,
  `id_asset` BIGINT NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

CREATE TABLE `t_asset_team_associations` (
  `id_asset` BIGINT NOT NULL,
  `id_team` BIGINT NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

CREATE TABLE `t_asset_user_associations` (
  `id_user` BIGINT NOT NULL,
  `id_asset` BIGINT NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

CREATE TABLE `t_asset_vendor_associations` (
  `id_vendor` BIGINT NOT NULL,
  `id_asset` BIGINT NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

CREATE TABLE `t_location_customer_associations` (
  `id_location` BIGINT NOT NULL,
  `id_customer` BIGINT NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

CREATE TABLE `t_location_file_associations` (
  `id_location` BIGINT NOT NULL,
  `id_file` BIGINT NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

CREATE TABLE `t_location_team_associations` (
  `id_location` BIGINT NOT NULL,
  `id_team` BIGINT NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

CREATE TABLE `t_location_user_associations` (
  `id_location` BIGINT NOT NULL,
  `id_user` BIGINT NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

CREATE TABLE `t_location_vendor_associations` (
  `id_location` BIGINT NOT NULL,
  `id_vendor` BIGINT NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

CREATE TABLE `t_meter_user_associations` (
  `id_meter` BIGINT NOT NULL,
  `id_user` BIGINT NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

CREATE TABLE `t_multi_parts_part_associations` (
  `id_part` BIGINT NOT NULL,
  `id_multi_parts` BIGINT NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

CREATE TABLE `t_part_customer_associations` (
  `id_part` BIGINT NOT NULL,
  `id_customer` BIGINT NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

CREATE TABLE `t_part_file_associations` (
  `id_part` BIGINT NOT NULL,
  `id_file` BIGINT NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

CREATE TABLE `t_part_team_associations` (
  `id_part` BIGINT NOT NULL,
  `id_team` BIGINT NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

CREATE TABLE `t_part_user_associations` (
  `id_part` BIGINT NOT NULL,
  `id_user` BIGINT NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

CREATE TABLE `t_part_vendor_associations` (
  `id_part` BIGINT NOT NULL,
  `id_vendor` BIGINT NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

CREATE TABLE `t_request_file_associations` (
  `id_file` BIGINT NOT NULL,
  `id_request` BIGINT NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

CREATE TABLE `t_team_user_associations` (
  `id_user` BIGINT NOT NULL,
  `id_team` BIGINT NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

CREATE TABLE `t_work_order_file_associations` (
  `id_file` BIGINT NOT NULL,
  `id_work_order` BIGINT NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

CREATE TABLE `task` (
  `id` BIGINT NOT NULL,
  `created_at` DATETIME(6) NOT NULL,
  `updated_at` DATETIME(6) NOT NULL,
  `created_by` BIGINT NULL,
  `updated_by` BIGINT NULL,
  `notes` VARCHAR(255) NULL,
  `value` VARCHAR(255) NULL,
  `company_id` BIGINT NOT NULL,
  `task_base_id` BIGINT NOT NULL,
  `work_order_id` BIGINT NULL,
  `preventive_maintenance_id` BIGINT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

CREATE TABLE `task_base` (
  `id` BIGINT NOT NULL,
  `created_at` DATETIME(6) NOT NULL,
  `updated_at` DATETIME(6) NOT NULL,
  `created_by` BIGINT NULL,
  `updated_by` BIGINT NULL,
  `label` VARCHAR(255) NOT NULL,
  `task_type` SMALLINT NULL,
  `company_id` BIGINT NOT NULL,
  `asset_id` BIGINT NULL,
  `meter_id` BIGINT NULL,
  `user_id` BIGINT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

CREATE TABLE `task_option` (
  `id` BIGINT NOT NULL,
  `created_at` DATETIME(6) NOT NULL,
  `updated_at` DATETIME(6) NOT NULL,
  `created_by` BIGINT NULL,
  `updated_by` BIGINT NULL,
  `label` VARCHAR(255) NULL,
  `company_id` BIGINT NOT NULL,
  `task_base_id` BIGINT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

CREATE TABLE `team` (
  `id` BIGINT NOT NULL,
  `created_at` DATETIME(6) NOT NULL,
  `updated_at` DATETIME(6) NOT NULL,
  `created_by` BIGINT NULL,
  `updated_by` BIGINT NULL,
  `description` LONGTEXT NULL,
  `name` VARCHAR(255) NOT NULL,
  `company_id` BIGINT NOT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

CREATE TABLE `time_category` (
  `id` BIGINT NOT NULL,
  `created_at` DATETIME(6) NOT NULL,
  `updated_at` DATETIME(6) NOT NULL,
  `created_by` BIGINT NULL,
  `updated_by` BIGINT NULL,
  `name` VARCHAR(255) NOT NULL,
  `company_settings_id` BIGINT NOT NULL,
  `description` LONGTEXT NULL,
  `is_demo` BIT(1) NOT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

CREATE TABLE `ui_configuration` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `requests` BIT(1) NOT NULL DEFAULT true,
  `locations` BIT(1) NOT NULL DEFAULT true,
  `meters` BIT(1) NOT NULL DEFAULT true,
  `vendors_and_customers` BIT(1) NOT NULL DEFAULT true,
  `company_settings_id` BIGINT NOT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

CREATE TABLE `user_app_stats` (
  `id` BIGINT NOT NULL,
  `created_at` DATETIME(6) NOT NULL,
  `updated_at` DATETIME(6) NOT NULL,
  `created_by` BIGINT NULL,
  `updated_by` BIGINT NULL,
  `completed_work_orders` INT NOT NULL DEFAULT 0,
  `app_sessions` INT NOT NULL DEFAULT 0,
  `last_review_prompt_at` DATETIME(6) NULL,
  `review_prompt_shown_count` INT NOT NULL DEFAULT 0,
  `review_click_count` INT NOT NULL DEFAULT 0,
  `has_rated_app` BIT(1) NOT NULL DEFAULT false,
  `feedback` LONGTEXT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

CREATE TABLE `user_invitation` (
  `id` BIGINT NOT NULL,
  `created_at` DATETIME(6) NOT NULL,
  `updated_at` DATETIME(6) NOT NULL,
  `created_by` BIGINT NULL,
  `updated_by` BIGINT NULL,
  `email` VARCHAR(255) NOT NULL,
  `role_id` BIGINT NOT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

CREATE TABLE `user_settings` (
  `id` BIGINT NOT NULL,
  `email_notified` BIT(1) NOT NULL,
  `email_updates_for_purchase_orders` BIT(1) NOT NULL,
  `email_updates_for_requests` BIT(1) NOT NULL,
  `email_updates_for_work_orders` BIT(1) NOT NULL,
  `stats_for_assigned_work_orders` BIT(1) NOT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

CREATE TABLE `vendor` (
  `id` BIGINT NOT NULL,
  `created_at` DATETIME(6) NOT NULL,
  `updated_at` DATETIME(6) NOT NULL,
  `created_by` BIGINT NULL,
  `updated_by` BIGINT NULL,
  `address` VARCHAR(255) NULL,
  `email` VARCHAR(255) NULL,
  `name` VARCHAR(255) NOT NULL,
  `phone` VARCHAR(255) NULL,
  `website` VARCHAR(255) NULL,
  `company_name` VARCHAR(255) NOT NULL,
  `description` LONGTEXT NULL,
  `rate` BIGINT NOT NULL,
  `vendor_type` VARCHAR(255) NULL,
  `company_id` BIGINT NOT NULL,
  `is_demo` BIT(1) NOT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

CREATE TABLE `verification_token` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `created_at` DATETIME(6) NULL,
  `expiry_date` DATETIME(6) NULL,
  `token` VARCHAR(255) NULL,
  `user_id` BIGINT NOT NULL,
  `payload` LONGTEXT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

CREATE TABLE `webhook_endpoint` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `url` VARCHAR(255) NOT NULL,
  `secret` VARCHAR(255) NOT NULL,
  `enabled` BIT(1) NOT NULL DEFAULT true,
  `serialize` BIT(1) NULL DEFAULT false,
  `event` VARCHAR(255) NOT NULL,
  `last_triggered_at` DATETIME(6) NULL,
  `company_id` BIGINT NOT NULL,
  `created_at` DATETIME(6) NOT NULL,
  `updated_at` DATETIME(6) NOT NULL,
  `created_by` BIGINT NULL,
  `updated_by` BIGINT NULL,
  `approved` BIT(1) NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

CREATE TABLE `webhook_endpoint_asset_statuses` (
  `webhook_endpoint_id` BIGINT NOT NULL,
  `asset_statuses` VARCHAR(255) NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

CREATE TABLE `webhook_endpoint_part_fields` (
  `webhook_endpoint_id` BIGINT NOT NULL,
  `part_fields` VARCHAR(255) NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

CREATE TABLE `webhook_endpoint_wo_fields` (
  `webhook_endpoint_id` BIGINT NOT NULL,
  `wo_fields` VARCHAR(255) NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

CREATE TABLE `webhook_endpoint_work_order_categories` (
  `webhook_endpoint_id` BIGINT NOT NULL,
  `work_order_categories_id` BIGINT NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

CREATE TABLE `webhook_endpoint_work_order_statuses` (
  `webhook_endpoint_id` BIGINT NOT NULL,
  `work_order_statuses` VARCHAR(255) NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

CREATE TABLE `work_order` (
  `id` BIGINT NOT NULL,
  `created_at` DATETIME(6) NOT NULL,
  `updated_at` DATETIME(6) NOT NULL,
  `created_by` BIGINT NULL,
  `updated_by` BIGINT NULL,
  `description` LONGTEXT NULL,
  `due_date` DATETIME(6) NULL,
  `estimated_duration` DOUBLE NOT NULL,
  `priority` SMALLINT NULL,
  `required_signature` BIT(1) NOT NULL,
  `title` VARCHAR(255) NOT NULL,
  `archived` BIT(1) NOT NULL,
  `completed_on` DATETIME(6) NULL,
  `feedback` LONGTEXT NULL,
  `status` SMALLINT NULL,
  `company_id` BIGINT NOT NULL,
  `asset_id` BIGINT NULL,
  `category_id` BIGINT NULL,
  `image_id` BIGINT NULL,
  `location_id` BIGINT NULL,
  `primary_user_id` BIGINT NULL,
  `team_id` BIGINT NULL,
  `completed_by_id` BIGINT NULL,
  `parent_preventive_maintenance_id` BIGINT NULL,
  `parent_request_id` BIGINT NULL,
  `signature_id` BIGINT NULL,
  `first_time_to_react` DATETIME(6) NULL,
  `estimated_start_date` DATETIME(6) NULL,
  `custom_id` VARCHAR(255) NULL,
  `signature` LONGTEXT NULL,
  `is_demo` BIT(1) NOT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

CREATE TABLE `work_order_assigned_to` (
  `work_order_id` BIGINT NOT NULL,
  `assigned_to_id` BIGINT NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

CREATE TABLE `work_order_aud` (
  `rev` INT NOT NULL,
  `revtype` INT NULL,
  `due_date` DATETIME(6) NULL,
  `priority` SMALLINT NULL,
  `estimated_duration` DOUBLE NULL,
  `description` LONGTEXT NULL,
  `title` VARCHAR(255) NULL,
  `required_signature` BIT(1) NULL,
  `image_id` BIGINT NULL,
  `category_id` BIGINT NULL,
  `location_id` BIGINT NULL,
  `team_id` BIGINT NULL,
  `primary_user_id` BIGINT NULL,
  `completed_by_id` BIGINT NULL,
  `completed_on` DATETIME(6) NULL,
  `status` SMALLINT NULL,
  `signature_id` BIGINT NULL,
  `archived` BIT(1) NULL,
  `parent_request_id` BIGINT NULL,
  `feedback` LONGTEXT NULL,
  `parent_preventive_maintenance_id` BIGINT NULL,
  `due_date_mod` BIT(1) NULL,
  `priority_mod` BIT(1) NULL,
  `estimated_duration_mod` BIT(1) NULL,
  `description_mod` BIT(1) NULL,
  `title_mod` BIT(1) NULL,
  `required_signature_mod` BIT(1) NULL,
  `image_mod` BIT(1) NULL,
  `category_mod` BIT(1) NULL,
  `location_mod` BIT(1) NULL,
  `team_mod` BIT(1) NULL,
  `primary_user_mod` BIT(1) NULL,
  `completed_by_mod` BIT(1) NULL,
  `completed_on_mod` BIT(1) NULL,
  `status_mod` BIT(1) NULL,
  `signature_mod` BIT(1) NULL,
  `archived_mod` BIT(1) NULL,
  `parent_request_mod` BIT(1) NULL,
  `feedback_mod` BIT(1) NULL,
  `parent_preventive_maintenance_mod` BIT(1) NULL,
  `asset_id` BIGINT NULL,
  `asset_mod` BIT(1) NULL,
  `id` BIGINT NOT NULL,
  `estimated_start_date` DATETIME(6) NULL,
  `estimated_start_date_mod` BIT(1) NULL,
  `custom_id` VARCHAR(255) NULL,
  `custom_id_mod` BIT(1) NULL DEFAULT false,
  `signature` LONGTEXT NULL,
  `is_demo` BIT(1) NULL,
  `is_demo_mod` BIT(1) NULL,
  PRIMARY KEY (`id`,`rev`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

CREATE TABLE `work_order_category` (
  `id` BIGINT NOT NULL,
  `created_at` DATETIME(6) NOT NULL,
  `updated_at` DATETIME(6) NOT NULL,
  `created_by` BIGINT NULL,
  `updated_by` BIGINT NULL,
  `name` VARCHAR(255) NOT NULL,
  `company_settings_id` BIGINT NOT NULL,
  `description` LONGTEXT NULL,
  `is_demo` BIT(1) NOT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

CREATE TABLE `work_order_configuration` (
  `id` BIGINT NOT NULL,
  `company_settings_id` BIGINT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

CREATE TABLE `work_order_customers` (
  `work_order_id` BIGINT NOT NULL,
  `customers_id` BIGINT NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

CREATE TABLE `work_order_files` (
  `work_order_id` BIGINT NOT NULL,
  `files_id` BIGINT NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

CREATE TABLE `work_order_history` (
  `id` BIGINT NOT NULL,
  `created_at` DATETIME(6) NOT NULL,
  `updated_at` DATETIME(6) NOT NULL,
  `created_by` BIGINT NULL,
  `updated_by` BIGINT NULL,
  `name` VARCHAR(255) NOT NULL,
  `user_id` BIGINT NOT NULL,
  `work_order_id` BIGINT NOT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

CREATE TABLE `work_order_meter_trigger` (
  `id` BIGINT NOT NULL,
  `created_at` DATETIME(6) NOT NULL,
  `updated_at` DATETIME(6) NOT NULL,
  `created_by` BIGINT NULL,
  `updated_by` BIGINT NULL,
  `description` LONGTEXT NULL,
  `due_date` DATETIME(6) NULL,
  `estimated_duration` DOUBLE NOT NULL,
  `priority` SMALLINT NULL,
  `required_signature` BIT(1) NOT NULL,
  `title` VARCHAR(255) NOT NULL,
  `name` VARCHAR(255) NOT NULL,
  `recurrent` BIT(1) NOT NULL,
  `trigger_condition` SMALLINT NOT NULL,
  `value` INT NOT NULL,
  `wait_before` INT NOT NULL,
  `company_id` BIGINT NOT NULL,
  `asset_id` BIGINT NULL,
  `category_id` BIGINT NULL,
  `image_id` BIGINT NULL,
  `location_id` BIGINT NULL,
  `primary_user_id` BIGINT NULL,
  `team_id` BIGINT NULL,
  `meter_id` BIGINT NOT NULL,
  `estimated_start_date` DATETIME(6) NULL,
  `episode_policy` VARCHAR(32) NOT NULL DEFAULT 'PER_EPISODE',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

CREATE TABLE `work_order_meter_trigger_assigned_to` (
  `work_order_meter_trigger_id` BIGINT NOT NULL,
  `assigned_to_id` BIGINT NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

CREATE TABLE `work_order_meter_trigger_customers` (
  `work_order_meter_trigger_id` BIGINT NOT NULL,
  `customers_id` BIGINT NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

CREATE TABLE `work_order_meter_trigger_files` (
  `work_order_meter_trigger_id` BIGINT NOT NULL,
  `files_id` BIGINT NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

CREATE TABLE `work_order_request_configuration` (
  `id` BIGINT NOT NULL,
  `company_settings_id` BIGINT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

CREATE TABLE `workflow` (
  `id` BIGINT NOT NULL,
  `created_at` DATETIME(6) NOT NULL,
  `updated_at` DATETIME(6) NOT NULL,
  `created_by` BIGINT NULL,
  `updated_by` BIGINT NULL,
  `main_condition` SMALLINT NOT NULL,
  `company_id` BIGINT NOT NULL,
  `action_id` BIGINT NOT NULL,
  `title` VARCHAR(255) NOT NULL,
  `enabled` BIT(1) NOT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

CREATE TABLE `workflow_action` (
  `id` BIGINT NOT NULL,
  `created_at` DATETIME(6) NOT NULL,
  `updated_at` DATETIME(6) NOT NULL,
  `created_by` BIGINT NULL,
  `updated_by` BIGINT NULL,
  `priority` SMALLINT NULL,
  `company_id` BIGINT NOT NULL,
  `asset_id` BIGINT NULL,
  `checklist_id` BIGINT NULL,
  `location_id` BIGINT NULL,
  `team_id` BIGINT NULL,
  `user_id` BIGINT NULL,
  `asset_status` SMALLINT NULL,
  `part_action` SMALLINT NULL,
  `purchase_order_action` SMALLINT NULL,
  `request_action` SMALLINT NULL,
  `task_action` SMALLINT NULL,
  `work_order_action` SMALLINT NULL,
  `purchase_order_category_id` BIGINT NULL,
  `vendor_id` BIGINT NULL,
  `work_order_category_id` BIGINT NULL,
  `number_value` INT NULL,
  `value` VARCHAR(255) NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

CREATE TABLE `workflow_condition` (
  `id` BIGINT NOT NULL,
  `created_at` DATETIME(6) NOT NULL,
  `updated_at` DATETIME(6) NOT NULL,
  `created_by` BIGINT NULL,
  `updated_by` BIGINT NULL,
  `created_time_end` INT NULL,
  `created_time_start` INT NULL,
  `priority` SMALLINT NULL,
  `company_id` BIGINT NOT NULL,
  `asset_id` BIGINT NULL,
  `location_id` BIGINT NULL,
  `team_id` BIGINT NULL,
  `user_id` BIGINT NULL,
  `label` VARCHAR(255) NULL,
  `number_value` INT NULL,
  `part_condition` SMALLINT NULL,
  `purchase_order_condition` SMALLINT NULL,
  `purchase_order_status` SMALLINT NULL,
  `request_condition` SMALLINT NULL,
  `task_condition` SMALLINT NULL,
  `value` VARCHAR(255) NULL,
  `work_order_condition` SMALLINT NULL,
  `work_order_status` SMALLINT NULL,
  `part_id` BIGINT NULL,
  `purchase_order_category_id` BIGINT NULL,
  `vendor_id` BIGINT NULL,
  `work_order_category_id` BIGINT NULL,
  `end_date` DATETIME(6) NULL,
  `start_date` DATETIME(6) NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

CREATE TABLE `workflow_secondary_conditions` (
  `workflow_id` BIGINT NOT NULL,
  `secondary_conditions_id` BIGINT NOT NULL,
  CONSTRAINT `uk_qpmhueid5ujh87ujsfu30bq3e` UNIQUE (`secondary_conditions_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

CREATE TABLE `hibernate_sequence` (next_val BIGINT NOT NULL) ENGINE=InnoDB;

INSERT INTO `hibernate_sequence` (next_val) VALUES (10001);

CREATE TABLE `work_order_AUD_rev_seq` (next_val BIGINT NOT NULL) ENGINE=InnoDB;

INSERT INTO `work_order_AUD_rev_seq` (next_val) VALUES (10001);

CREATE TABLE `revinfo_revision_id_seq` (next_val BIGINT NOT NULL) ENGINE=InnoDB;

INSERT INTO `revinfo_revision_id_seq` (next_val) VALUES (10001);

CREATE TABLE `additional_cost_seq` (next_val BIGINT NOT NULL) ENGINE=InnoDB;

INSERT INTO `additional_cost_seq` (next_val) VALUES (10001);

CREATE TABLE `asset_seq` (next_val BIGINT NOT NULL) ENGINE=InnoDB;

INSERT INTO `asset_seq` (next_val) VALUES (10001);

CREATE TABLE `asset_category_seq` (next_val BIGINT NOT NULL) ENGINE=InnoDB;

INSERT INTO `asset_category_seq` (next_val) VALUES (10001);

CREATE TABLE `asset_downtime_seq` (next_val BIGINT NOT NULL) ENGINE=InnoDB;

INSERT INTO `asset_downtime_seq` (next_val) VALUES (10001);

CREATE TABLE `checklist_seq` (next_val BIGINT NOT NULL) ENGINE=InnoDB;

INSERT INTO `checklist_seq` (next_val) VALUES (10001);

CREATE TABLE `company_seq` (next_val BIGINT NOT NULL) ENGINE=InnoDB;

INSERT INTO `company_seq` (next_val) VALUES (10001);

CREATE TABLE `company_settings_seq` (next_val BIGINT NOT NULL) ENGINE=InnoDB;

INSERT INTO `company_settings_seq` (next_val) VALUES (10001);

CREATE TABLE `cost_category_seq` (next_val BIGINT NOT NULL) ENGINE=InnoDB;

INSERT INTO `cost_category_seq` (next_val) VALUES (10001);

CREATE TABLE `currency_seq` (next_val BIGINT NOT NULL) ENGINE=InnoDB;

INSERT INTO `currency_seq` (next_val) VALUES (10001);

CREATE TABLE `customer_seq` (next_val BIGINT NOT NULL) ENGINE=InnoDB;

INSERT INTO `customer_seq` (next_val) VALUES (10001);

CREATE TABLE `custom_field_seq` (next_val BIGINT NOT NULL) ENGINE=InnoDB;

INSERT INTO `custom_field_seq` (next_val) VALUES (10001);

CREATE TABLE `custom_sequence_seq` (next_val BIGINT NOT NULL) ENGINE=InnoDB;

INSERT INTO `custom_sequence_seq` (next_val) VALUES (10001);

CREATE TABLE `deprecation_seq` (next_val BIGINT NOT NULL) ENGINE=InnoDB;

INSERT INTO `deprecation_seq` (next_val) VALUES (10001);

CREATE TABLE `field_configuration_seq` (next_val BIGINT NOT NULL) ENGINE=InnoDB;

INSERT INTO `field_configuration_seq` (next_val) VALUES (10001);

CREATE TABLE `file_seq` (next_val BIGINT NOT NULL) ENGINE=InnoDB;

INSERT INTO `file_seq` (next_val) VALUES (10001);

CREATE TABLE `floor_plan_seq` (next_val BIGINT NOT NULL) ENGINE=InnoDB;

INSERT INTO `floor_plan_seq` (next_val) VALUES (10001);

CREATE TABLE `general_preferences_seq` (next_val BIGINT NOT NULL) ENGINE=InnoDB;

INSERT INTO `general_preferences_seq` (next_val) VALUES (10001);

CREATE TABLE `labor_seq` (next_val BIGINT NOT NULL) ENGINE=InnoDB;

INSERT INTO `labor_seq` (next_val) VALUES (10001);

CREATE TABLE `location_seq` (next_val BIGINT NOT NULL) ENGINE=InnoDB;

INSERT INTO `location_seq` (next_val) VALUES (10001);

CREATE TABLE `meter_seq` (next_val BIGINT NOT NULL) ENGINE=InnoDB;

INSERT INTO `meter_seq` (next_val) VALUES (10001);

CREATE TABLE `meter_category_seq` (next_val BIGINT NOT NULL) ENGINE=InnoDB;

INSERT INTO `meter_category_seq` (next_val) VALUES (10001);

CREATE TABLE `multi_parts_seq` (next_val BIGINT NOT NULL) ENGINE=InnoDB;

INSERT INTO `multi_parts_seq` (next_val) VALUES (10001);

CREATE TABLE `notification_seq` (next_val BIGINT NOT NULL) ENGINE=InnoDB;

INSERT INTO `notification_seq` (next_val) VALUES (10001);

CREATE TABLE `own_user_seq` (next_val BIGINT NOT NULL) ENGINE=InnoDB;

INSERT INTO `own_user_seq` (next_val) VALUES (10001);

CREATE TABLE `part_seq` (next_val BIGINT NOT NULL) ENGINE=InnoDB;

INSERT INTO `part_seq` (next_val) VALUES (10001);

CREATE TABLE `part_category_seq` (next_val BIGINT NOT NULL) ENGINE=InnoDB;

INSERT INTO `part_category_seq` (next_val) VALUES (10001);

CREATE TABLE `part_quantity_seq` (next_val BIGINT NOT NULL) ENGINE=InnoDB;

INSERT INTO `part_quantity_seq` (next_val) VALUES (10001);

CREATE TABLE `preventive_maintenance_seq` (next_val BIGINT NOT NULL) ENGINE=InnoDB;

INSERT INTO `preventive_maintenance_seq` (next_val) VALUES (10001);

CREATE TABLE `purchase_order_seq` (next_val BIGINT NOT NULL) ENGINE=InnoDB;

INSERT INTO `purchase_order_seq` (next_val) VALUES (10001);

CREATE TABLE `purchase_order_category_seq` (next_val BIGINT NOT NULL) ENGINE=InnoDB;

INSERT INTO `purchase_order_category_seq` (next_val) VALUES (10001);

CREATE TABLE `push_notification_token_seq` (next_val BIGINT NOT NULL) ENGINE=InnoDB;

INSERT INTO `push_notification_token_seq` (next_val) VALUES (10001);

CREATE TABLE `reading_seq` (next_val BIGINT NOT NULL) ENGINE=InnoDB;

INSERT INTO `reading_seq` (next_val) VALUES (10001);

CREATE TABLE `relation_seq` (next_val BIGINT NOT NULL) ENGINE=InnoDB;

INSERT INTO `relation_seq` (next_val) VALUES (10001);

CREATE TABLE `request_seq` (next_val BIGINT NOT NULL) ENGINE=InnoDB;

INSERT INTO `request_seq` (next_val) VALUES (10001);

CREATE TABLE `role_seq` (next_val BIGINT NOT NULL) ENGINE=InnoDB;

INSERT INTO `role_seq` (next_val) VALUES (10001);

CREATE TABLE `schedule_seq` (next_val BIGINT NOT NULL) ENGINE=InnoDB;

INSERT INTO `schedule_seq` (next_val) VALUES (10001);

CREATE TABLE `subscription_seq` (next_val BIGINT NOT NULL) ENGINE=InnoDB;

INSERT INTO `subscription_seq` (next_val) VALUES (10001);

CREATE TABLE `subscription_change_request_seq` (next_val BIGINT NOT NULL) ENGINE=InnoDB;

INSERT INTO `subscription_change_request_seq` (next_val) VALUES (10001);

CREATE TABLE `subscription_plan_seq` (next_val BIGINT NOT NULL) ENGINE=InnoDB;

INSERT INTO `subscription_plan_seq` (next_val) VALUES (10001);

CREATE TABLE `super_account_relation_seq` (next_val BIGINT NOT NULL) ENGINE=InnoDB;

INSERT INTO `super_account_relation_seq` (next_val) VALUES (10001);

CREATE TABLE `task_seq` (next_val BIGINT NOT NULL) ENGINE=InnoDB;

INSERT INTO `task_seq` (next_val) VALUES (10001);

CREATE TABLE `task_base_seq` (next_val BIGINT NOT NULL) ENGINE=InnoDB;

INSERT INTO `task_base_seq` (next_val) VALUES (10001);

CREATE TABLE `task_option_seq` (next_val BIGINT NOT NULL) ENGINE=InnoDB;

INSERT INTO `task_option_seq` (next_val) VALUES (10001);

CREATE TABLE `team_seq` (next_val BIGINT NOT NULL) ENGINE=InnoDB;

INSERT INTO `team_seq` (next_val) VALUES (10001);

CREATE TABLE `time_category_seq` (next_val BIGINT NOT NULL) ENGINE=InnoDB;

INSERT INTO `time_category_seq` (next_val) VALUES (10001);

CREATE TABLE `ui_configuration_seq` (next_val BIGINT NOT NULL) ENGINE=InnoDB;

INSERT INTO `ui_configuration_seq` (next_val) VALUES (10001);

CREATE TABLE `user_invitation_seq` (next_val BIGINT NOT NULL) ENGINE=InnoDB;

INSERT INTO `user_invitation_seq` (next_val) VALUES (10001);

CREATE TABLE `user_settings_seq` (next_val BIGINT NOT NULL) ENGINE=InnoDB;

INSERT INTO `user_settings_seq` (next_val) VALUES (10001);

CREATE TABLE `vendor_seq` (next_val BIGINT NOT NULL) ENGINE=InnoDB;

INSERT INTO `vendor_seq` (next_val) VALUES (10001);

CREATE TABLE `verification_token_seq` (next_val BIGINT NOT NULL) ENGINE=InnoDB;

INSERT INTO `verification_token_seq` (next_val) VALUES (10001);

CREATE TABLE `workflow_seq` (next_val BIGINT NOT NULL) ENGINE=InnoDB;

INSERT INTO `workflow_seq` (next_val) VALUES (10001);

CREATE TABLE `workflow_action_seq` (next_val BIGINT NOT NULL) ENGINE=InnoDB;

INSERT INTO `workflow_action_seq` (next_val) VALUES (10001);

CREATE TABLE `workflow_condition_seq` (next_val BIGINT NOT NULL) ENGINE=InnoDB;

INSERT INTO `workflow_condition_seq` (next_val) VALUES (10001);

CREATE TABLE `work_order_seq` (next_val BIGINT NOT NULL) ENGINE=InnoDB;

INSERT INTO `work_order_seq` (next_val) VALUES (10001);

CREATE TABLE `work_order_category_seq` (next_val BIGINT NOT NULL) ENGINE=InnoDB;

INSERT INTO `work_order_category_seq` (next_val) VALUES (10001);

CREATE TABLE `work_order_configuration_seq` (next_val BIGINT NOT NULL) ENGINE=InnoDB;

INSERT INTO `work_order_configuration_seq` (next_val) VALUES (10001);

CREATE TABLE `work_order_history_seq` (next_val BIGINT NOT NULL) ENGINE=InnoDB;

INSERT INTO `work_order_history_seq` (next_val) VALUES (10001);

CREATE TABLE `work_order_meter_trigger_seq` (next_val BIGINT NOT NULL) ENGINE=InnoDB;

INSERT INTO `work_order_meter_trigger_seq` (next_val) VALUES (10001);

CREATE TABLE `work_order_request_configuration_seq` (next_val BIGINT NOT NULL) ENGINE=InnoDB;

INSERT INTO `work_order_request_configuration_seq` (next_val) VALUES (10001);

CREATE TABLE `request_portal_seq` (next_val BIGINT NOT NULL) ENGINE=InnoDB;

INSERT INTO `request_portal_seq` (next_val) VALUES (10001);

CREATE TABLE `request_portal_field_seq` (next_val BIGINT NOT NULL) ENGINE=InnoDB;

INSERT INTO `request_portal_field_seq` (next_val) VALUES (10001);

CREATE TABLE `api_key_seq` (next_val BIGINT NOT NULL) ENGINE=InnoDB;

INSERT INTO `api_key_seq` (next_val) VALUES (10001);

CREATE TABLE `webhook_endpoint_seq` (next_val BIGINT NOT NULL) ENGINE=InnoDB;

INSERT INTO `webhook_endpoint_seq` (next_val) VALUES (10001);

CREATE TABLE `comment_seq` (next_val BIGINT NOT NULL) ENGINE=InnoDB;

INSERT INTO `comment_seq` (next_val) VALUES (10001);

CREATE TABLE `company_custom_field_id_seq` (next_val BIGINT NOT NULL) ENGINE=InnoDB;

INSERT INTO `company_custom_field_id_seq` (next_val) VALUES (10001);

CREATE TABLE `company_custom_field_seq` (next_val BIGINT NOT NULL) ENGINE=InnoDB;

INSERT INTO `company_custom_field_seq` (next_val) VALUES (10001);

CREATE TABLE `custom_field_value_seq` (next_val BIGINT NOT NULL) ENGINE=InnoDB;

INSERT INTO `custom_field_value_seq` (next_val) VALUES (10001);

CREATE TABLE `shift_configuration_seq` (next_val BIGINT NOT NULL) ENGINE=InnoDB;

INSERT INTO `shift_configuration_seq` (next_val) VALUES (10001);

CREATE TABLE `user_app_stats_seq` (next_val BIGINT NOT NULL) ENGINE=InnoDB;

INSERT INTO `user_app_stats_seq` (next_val) VALUES (10001);

CREATE TABLE `part_transaction_seq` (next_val BIGINT NOT NULL) ENGINE=InnoDB;

INSERT INTO `part_transaction_seq` (next_val) VALUES (10001);

CREATE INDEX `idx_part_multi_parts_part_id` ON `t_multi_parts_part_associations` (`id_part`);

CREATE INDEX `idx_part_multi_parts_multi_parts_id` ON `t_multi_parts_part_associations` (`id_multi_parts`);

CREATE INDEX `idx_user_team_user_id` ON `t_team_user_associations` (`id_user`);

CREATE INDEX `idx_user_team_team_id` ON `t_team_user_associations` (`id_team`);

CREATE INDEX `idx_part_customer_part_id` ON `t_part_customer_associations` (`id_part`);

CREATE INDEX `idx_customer_part_customer_id` ON `t_part_customer_associations` (`id_customer`);

CREATE INDEX `idx_asset_team_asset_id` ON `t_asset_team_associations` (`id_asset`);

CREATE INDEX `idx_team_asset_team_id` ON `t_asset_team_associations` (`id_team`);

CREATE INDEX `idx_file_asset_file_id` ON `t_asset_file_associations` (`id_file`);

CREATE INDEX `idx_file_asset_asset_id` ON `t_asset_file_associations` (`id_asset`);

CREATE INDEX `idx_part_quantity_company_id` ON `part_quantity` (`company_id`);

CREATE INDEX `idx_location_customer_customer_id` ON `t_location_customer_associations` (`id_customer`);

CREATE INDEX `idx_customer_location_location_id` ON `t_location_customer_associations` (`id_location`);

CREATE INDEX `idx_user_location_user_id` ON `t_location_user_associations` (`id_user`);

CREATE INDEX `idx_user_location_location_id` ON `t_location_user_associations` (`id_location`);

CREATE INDEX `idx_file_company_id` ON `file` (`company_id`);

CREATE INDEX `idx_part_user_part_id` ON `t_part_user_associations` (`id_part`);

CREATE INDEX `idx_user_part_user_id` ON `t_part_user_associations` (`id_user`);

CREATE INDEX `idx_part_vendor_part_id` ON `t_part_vendor_associations` (`id_part`);

CREATE INDEX `idx_vendor_part_vendor_id` ON `t_part_vendor_associations` (`id_vendor`);

CREATE INDEX `idx_meter_user_meter_id` ON `t_meter_user_associations` (`id_meter`);

CREATE INDEX `idx_meter_user_user_id` ON `t_meter_user_associations` (`id_user`);

CREATE INDEX `idx_vendor_location_vendor_id` ON `t_location_vendor_associations` (`id_vendor`);

CREATE INDEX `idx_vendor_location_location_id` ON `t_location_vendor_associations` (`id_location`);

CREATE INDEX `idx_part_team_part_id` ON `t_part_team_associations` (`id_part`);

CREATE INDEX `idx_part_team_team_id` ON `t_part_team_associations` (`id_team`);

CREATE INDEX `idx_file_work_order_file_id` ON `t_work_order_file_associations` (`id_file`);

CREATE INDEX `idx_file_work_order_work_order_id` ON `t_work_order_file_associations` (`id_work_order`);

CREATE INDEX `idx_location_file_location_id` ON `t_location_file_associations` (`id_location`);

CREATE INDEX `idx_file_location_file_id` ON `t_location_file_associations` (`id_file`);

CREATE INDEX `idx_file_request_file_id` ON `t_request_file_associations` (`id_file`);

CREATE INDEX `idx_file_request_request_id` ON `t_request_file_associations` (`id_request`);

CREATE INDEX `idx_customer_asset_customer_id` ON `t_asset_customer_associations` (`id_customer`);

CREATE INDEX `idx_customer_asset_asset_id` ON `t_asset_customer_associations` (`id_asset`);

CREATE INDEX `idx_user_asset_asset_id` ON `t_asset_user_associations` (`id_asset`);

CREATE INDEX `idx_asset_user_user_id` ON `t_asset_user_associations` (`id_user`);

CREATE INDEX `idx_location_team_location_id` ON `t_location_team_associations` (`id_location`);

CREATE INDEX `idx_team_location_team_id` ON `t_location_team_associations` (`id_team`);

CREATE INDEX `idx_asset_part_asset_id` ON `t_asset_part_associations` (`id_asset`);

CREATE INDEX `idx_asset_part_part_id` ON `t_asset_part_associations` (`id_part`);

CREATE INDEX `idx_part_file_part_id` ON `t_part_file_associations` (`id_part`);

CREATE INDEX `idx_file_part_file_id` ON `t_part_file_associations` (`id_file`);

CREATE INDEX `idx_vendor_asset_vendor_id` ON `t_asset_vendor_associations` (`id_vendor`);

CREATE INDEX `idx_vendor_asset_asset_id` ON `t_asset_vendor_associations` (`id_asset`);

CREATE INDEX `idx_company_settings_company_id` ON `company_settings` (`company_id`);

CREATE INDEX `idx_company_demo` ON `company` (`demo`);

CREATE INDEX `idx_asset_downtime_company_id` ON `asset_downtime` (`company_id`);

CREATE INDEX `idx_deprecation_company_id` ON `deprecation` (`company_id`);

CREATE INDEX `idx_customer_company_id` ON `customer` (`company_id`);

CREATE INDEX `idx_purchase_order_company_id` ON `purchase_order` (`company_id`);

CREATE INDEX `idx_multi_parts_company_id` ON `multi_parts` (`company_id`);

CREATE INDEX `idx_preventive_maintenance_company_id` ON `preventive_maintenance` (`company_id`);

CREATE INDEX `idx_meter_company_id` ON `meter` (`company_id`);

CREATE INDEX `idx_relation_company_id` ON `relation` (`company_id`);

CREATE INDEX `idx_location_company_id` ON `location` (`company_id`);

CREATE INDEX `idx_location_parent_location_id` ON `location` (`parent_location_id`);

CREATE UNIQUE INDEX `notification_event_recipient_idx` ON `notification` (`event_company_id`,`event_id`,`user_id`,`event_purpose`);

CREATE INDEX `idx_request_company_id` ON `request` (`company_id`);

CREATE INDEX `ix_request_company_work_order` ON `request` (`company_id`,`work_order_id`);

CREATE INDEX `idx_part_company_id` ON `part` (`company_id`);

CREATE INDEX `idx_work_order_company_id` ON `work_order` (`company_id`);

CREATE INDEX `idx_work_order_company_asset_created` ON `work_order` (`company_id`,`asset_id`,`created_at`,`id`);

CREATE INDEX `idx_vendor_company_id` ON `vendor` (`company_id`);

CREATE INDEX `idx_task_base_company_id` ON `task_base` (`company_id`);

CREATE INDEX `idx_task_option_company_id` ON `task_option` (`company_id`);

CREATE INDEX `idx_task_company_id` ON `task` (`company_id`);

CREATE INDEX `idx_team_company_id` ON `team` (`company_id`);

CREATE INDEX `idx_work_order_meter_trigger_company_id` ON `work_order_meter_trigger` (`company_id`);

CREATE INDEX `idx_asset_company_id` ON `asset` (`company_id`);

CREATE INDEX `idx_asset_parent_asset_id` ON `asset` (`parent_asset_id`);

CREATE INDEX `idx_asset_location_id` ON `asset` (`location_id`);

CREATE INDEX `idx_subscription_change_request_company_id` ON `subscription_change_request` (`company_id`);

CREATE INDEX `idx_custom_sequence_company_id` ON `custom_sequence` (`company_id`);

CREATE INDEX `idx_part_consumption_company_id` ON `part_transaction` (`company_id`);

CREATE INDEX `idx_qrtz_j_req_recovery` ON `QRTZ_JOB_DETAILS` (`sched_name`,`requests_recovery`);

CREATE INDEX `idx_qrtz_j_grp` ON `QRTZ_JOB_DETAILS` (`sched_name`,`job_group`);

CREATE INDEX `idx_qrtz_t_j` ON `QRTZ_TRIGGERS` (`sched_name`,`job_name`,`job_group`);

CREATE INDEX `idx_qrtz_t_jg` ON `QRTZ_TRIGGERS` (`sched_name`,`job_group`);

CREATE INDEX `idx_qrtz_t_c` ON `QRTZ_TRIGGERS` (`sched_name`,`calendar_name`);

CREATE INDEX `idx_qrtz_t_g` ON `QRTZ_TRIGGERS` (`sched_name`,`trigger_group`);

CREATE INDEX `idx_qrtz_t_state` ON `QRTZ_TRIGGERS` (`sched_name`,`trigger_state`);

CREATE INDEX `idx_qrtz_t_n_state` ON `QRTZ_TRIGGERS` (`sched_name`,`trigger_name`,`trigger_group`,`trigger_state`);

CREATE INDEX `idx_qrtz_t_n_g_state` ON `QRTZ_TRIGGERS` (`sched_name`,`trigger_group`,`trigger_state`);

CREATE INDEX `idx_qrtz_t_next_fire_time` ON `QRTZ_TRIGGERS` (`sched_name`,`next_fire_time`);

CREATE INDEX `idx_qrtz_t_nft_st` ON `QRTZ_TRIGGERS` (`sched_name`,`trigger_state`,`next_fire_time`);

CREATE INDEX `idx_qrtz_t_nft_misfire` ON `QRTZ_TRIGGERS` (`sched_name`,`misfire_instr`,`next_fire_time`);

CREATE INDEX `idx_qrtz_t_nft_st_misfire` ON `QRTZ_TRIGGERS` (`sched_name`,`misfire_instr`,`next_fire_time`,`trigger_state`);

CREATE INDEX `idx_qrtz_t_nft_st_misfire_grp` ON `QRTZ_TRIGGERS` (`sched_name`,`misfire_instr`,`next_fire_time`,`trigger_group`,`trigger_state`);

CREATE INDEX `idx_own_user_company_id` ON `own_user` (`company_id`);

CREATE INDEX `idx_qrtz_f_t_j` ON `QRTZ_FIRED_TRIGGERS` (`sched_name`,`job_name`,`job_group`);

CREATE INDEX `idx_qrtz_f_t_jg` ON `QRTZ_FIRED_TRIGGERS` (`sched_name`,`job_group`);

CREATE INDEX `idx_qrtz_f_t_t_tg` ON `QRTZ_FIRED_TRIGGERS` (`sched_name`,`trigger_name`,`trigger_group`);

CREATE INDEX `idx_qrtz_f_t_tg` ON `QRTZ_FIRED_TRIGGERS` (`sched_name`,`trigger_group`);

CREATE INDEX `idx_workflow_condition_company_id` ON `workflow_condition` (`company_id`);

CREATE INDEX `idx_workflow_action_company_id` ON `workflow_action` (`company_id`);

CREATE INDEX `idx_workflow_company_id` ON `workflow` (`company_id`);

CREATE INDEX `idx_labor_company_id` ON `labor` (`company_id`);

CREATE INDEX `idx_company_custom_field_settings` ON `custom_field` (`company_settings_id`);

CREATE INDEX `idx_company_custom_field_type` ON `custom_field` (`field_type`);

CREATE INDEX `idx_company_custom_field_entity_type` ON `custom_field` (`entity_type`);

CREATE INDEX `idx_field_options_field_id` ON `company_custom_field_options` (`field_id`);

CREATE INDEX `idx_custom_field_value_work_order` ON `custom_field_value` (`work_order_id`);

CREATE INDEX `idx_custom_field_value_asset` ON `custom_field_value` (`asset_id`);

CREATE INDEX `idx_custom_field_value_location` ON `custom_field_value` (`location_id`);

CREATE INDEX `idx_custom_field_value_customer` ON `custom_field_value` (`customer_id`);

CREATE INDEX `idx_custom_field_value_vendor` ON `custom_field_value` (`vendor_id`);

CREATE INDEX `idx_custom_field_value_part` ON `custom_field_value` (`part_id`);

CREATE INDEX `idx_custom_field_value_meter` ON `custom_field_value` (`meter_id`);

CREATE INDEX `idx_custom_field_value_preventive_maintenance` ON `custom_field_value` (`preventive_maintenance_id`);

CREATE INDEX `idx_custom_field_value_request` ON `custom_field_value` (`request_id`);

CREATE INDEX `idx_custom_field_value_work_order_meter_trigger` ON `custom_field_value` (`work_order_meter_trigger_id`);

CREATE INDEX `idx_meter_trigger_execution_company` ON `meter_trigger_execution` (`company_id`,`work_order_id`);

CREATE INDEX `event_retry_recover_idx` ON `event_retry` (`state`,`lease_until`);

CREATE INDEX `domain_event_outbox_pending_idx` ON `domain_event_outbox` (`status`,`next_attempt_at`,`created_at`);

CREATE INDEX `event_delivery_ready_idx` ON `event_delivery` (`state`,`next_attempt_at`,`lease_until`);

CREATE INDEX `idx_auth_session_user_created` ON `auth_session` (`user_id`,`created_at` DESC);

CREATE INDEX `idx_refresh_token_session` ON `refresh_token` (`session_id`);

CREATE INDEX `ix_request_link_company` ON `request_link_audit` (`company_id`,`linked_at` DESC);

CREATE INDEX `ix_sla_company_due` ON `maintenance_sla_case` (`company_id`,`repair_due_at`,`work_order_id`);

CREATE INDEX `ix_sla_unprocessed_response` ON `maintenance_sla_case` (`response_event_id`,`response_due_at`);

CREATE INDEX `ix_sla_unprocessed_repair` ON `maintenance_sla_case` (`repair_event_id`,`repair_due_at`);

ALTER TABLE `t_multi_parts_part_associations` ADD CONSTRAINT `fk68kd07kt2lpb0oach23f1spmm` FOREIGN KEY (`id_multi_parts`) REFERENCES `multi_parts` (`id`) ON DELETE CASCADE;

ALTER TABLE `t_multi_parts_part_associations` ADD CONSTRAINT `fkh2jvrtqx4b25nfqafdlc92aek` FOREIGN KEY (`id_part`) REFERENCES `part` (`id`) ON DELETE CASCADE;

ALTER TABLE `t_team_user_associations` ADD CONSTRAINT `fk18sbate5bpyyxby399j40x7re` FOREIGN KEY (`id_user`) REFERENCES `own_user` (`id`) ON DELETE CASCADE;

ALTER TABLE `t_team_user_associations` ADD CONSTRAINT `fk2p1ho57toyn0pe8vq0cgtd10o` FOREIGN KEY (`id_team`) REFERENCES `team` (`id`) ON DELETE CASCADE;

ALTER TABLE `t_part_customer_associations` ADD CONSTRAINT `fk1lqox0hm74ii88ptyp0ks4hx6` FOREIGN KEY (`id_part`) REFERENCES `part` (`id`) ON DELETE CASCADE;

ALTER TABLE `t_part_customer_associations` ADD CONSTRAINT `fk1uem77bfgvi2e1a5bd2c9juoc` FOREIGN KEY (`id_customer`) REFERENCES `customer` (`id`) ON DELETE CASCADE;

ALTER TABLE `t_asset_team_associations` ADD CONSTRAINT `fk1wctjv9n7v7os67th2j241t0s` FOREIGN KEY (`id_team`) REFERENCES `team` (`id`) ON DELETE CASCADE;

ALTER TABLE `t_asset_team_associations` ADD CONSTRAINT `fkqr34hfl16wsv4krhpdcatpeft` FOREIGN KEY (`id_asset`) REFERENCES `asset` (`id`) ON DELETE CASCADE;

ALTER TABLE `t_asset_file_associations` ADD CONSTRAINT `fk20hrlr3c09srq7yt5ibdal2pv` FOREIGN KEY (`id_asset`) REFERENCES `asset` (`id`) ON DELETE CASCADE;

ALTER TABLE `t_asset_file_associations` ADD CONSTRAINT `fkefr6a3an7giyclgfs4e2vqjp0` FOREIGN KEY (`id_file`) REFERENCES `file` (`id`) ON DELETE CASCADE;

ALTER TABLE `part_quantity` ADD CONSTRAINT `fk21q7kq7mtnvshipi5oym9t2b3` FOREIGN KEY (`part_id`) REFERENCES `part` (`id`) ON DELETE CASCADE;

ALTER TABLE `part_quantity` ADD CONSTRAINT `fkb8l63n0csjq7q33wwbkvsj61u` FOREIGN KEY (`purchase_order_id`) REFERENCES `purchase_order` (`id`) ON DELETE CASCADE;

ALTER TABLE `part_quantity` ADD CONSTRAINT `fkoosoyfli9uk7bitxrpkrthrnj` FOREIGN KEY (`work_order_id`) REFERENCES `work_order` (`id`) ON DELETE CASCADE;

ALTER TABLE `part_quantity` ADD CONSTRAINT `fkqipxpeva75fa76890xnl4u0fx` FOREIGN KEY (`company_id`) REFERENCES `company` (`id`) ON DELETE CASCADE;

ALTER TABLE `t_location_customer_associations` ADD CONSTRAINT `fk26ljyp1uaoiw65qxqxtfr8i44` FOREIGN KEY (`id_customer`) REFERENCES `customer` (`id`) ON DELETE CASCADE;

ALTER TABLE `t_location_customer_associations` ADD CONSTRAINT `fkrohs7o68xw77yvwvlo0prue70` FOREIGN KEY (`id_location`) REFERENCES `location` (`id`) ON DELETE CASCADE;

ALTER TABLE `t_location_user_associations` ADD CONSTRAINT `fk2vp8mot9fhshwsh8flivg2e61` FOREIGN KEY (`id_location`) REFERENCES `location` (`id`) ON DELETE CASCADE;

ALTER TABLE `t_location_user_associations` ADD CONSTRAINT `fke43mw9vk8u1y3ugxjhwt19s0g` FOREIGN KEY (`id_user`) REFERENCES `own_user` (`id`) ON DELETE CASCADE;

ALTER TABLE `file` ADD CONSTRAINT `fkipblk8l80i440v7n7be1gnyet` FOREIGN KEY (`company_id`) REFERENCES `company` (`id`) ON DELETE CASCADE;

ALTER TABLE `file` ADD CONSTRAINT `fkkl8espv0gf1wcu82gvj2lccbm` FOREIGN KEY (`task_id`) REFERENCES `task` (`id`) ON DELETE CASCADE;

ALTER TABLE `t_part_user_associations` ADD CONSTRAINT `fk690l6jljarx0xx5mq51qhgq64` FOREIGN KEY (`id_part`) REFERENCES `part` (`id`) ON DELETE CASCADE;

ALTER TABLE `t_part_user_associations` ADD CONSTRAINT `fk82khjnm7mtmukbbuwnm5okidn` FOREIGN KEY (`id_user`) REFERENCES `own_user` (`id`) ON DELETE CASCADE;

ALTER TABLE `t_part_vendor_associations` ADD CONSTRAINT `fk7d9ha6pxi5gajdqeds5ekdcw5` FOREIGN KEY (`id_part`) REFERENCES `part` (`id`) ON DELETE CASCADE;

ALTER TABLE `t_part_vendor_associations` ADD CONSTRAINT `fklhnjg0qhqks9ob72oybq4xk73` FOREIGN KEY (`id_vendor`) REFERENCES `vendor` (`id`) ON DELETE CASCADE;

ALTER TABLE `t_meter_user_associations` ADD CONSTRAINT `fk8ikpoqiwcc3i40wdfjl2dynv7` FOREIGN KEY (`id_meter`) REFERENCES `meter` (`id`) ON DELETE CASCADE;

ALTER TABLE `t_meter_user_associations` ADD CONSTRAINT `fkctimil7gh7ea6xa1n98kea0id` FOREIGN KEY (`id_user`) REFERENCES `own_user` (`id`) ON DELETE CASCADE;

ALTER TABLE `t_location_vendor_associations` ADD CONSTRAINT `fk8qdsmekvft0a8cjricw0fyjeq` FOREIGN KEY (`id_vendor`) REFERENCES `vendor` (`id`) ON DELETE CASCADE;

ALTER TABLE `t_location_vendor_associations` ADD CONSTRAINT `fkb6alk3ojlofcs76q4hnce764b` FOREIGN KEY (`id_location`) REFERENCES `location` (`id`) ON DELETE CASCADE;

ALTER TABLE `t_part_team_associations` ADD CONSTRAINT `fk8xln4ekdxsmga312jsw9lqxg8` FOREIGN KEY (`id_team`) REFERENCES `team` (`id`) ON DELETE CASCADE;

ALTER TABLE `t_part_team_associations` ADD CONSTRAINT `fkp3whk5onr642t9ts5p2jmuda9` FOREIGN KEY (`id_part`) REFERENCES `part` (`id`) ON DELETE CASCADE;

ALTER TABLE `verification_token` ADD CONSTRAINT `fk9sh6q5nyk1pmm19t235i67sxk` FOREIGN KEY (`user_id`) REFERENCES `own_user` (`id`) ON DELETE CASCADE;

ALTER TABLE `checklist_task_bases` ADD CONSTRAINT `fkd34phs94o7382otdgl82mcoua` FOREIGN KEY (`checklist_id`) REFERENCES `checklist` (`id`) ON DELETE CASCADE;

ALTER TABLE `checklist_task_bases` ADD CONSTRAINT `fkhk7924rrpw72xmw6xcnd04xtf` FOREIGN KEY (`task_bases_id`) REFERENCES `task_base` (`id`) ON DELETE CASCADE;

ALTER TABLE `t_work_order_file_associations` ADD CONSTRAINT `fkghh8ggrdasrs4wij0xneb4qhj` FOREIGN KEY (`id_file`) REFERENCES `file` (`id`) ON DELETE CASCADE;

ALTER TABLE `t_work_order_file_associations` ADD CONSTRAINT `fkgky3mpbw56uh8apli8ys6w4lv` FOREIGN KEY (`id_work_order`) REFERENCES `work_order` (`id`) ON DELETE CASCADE;

ALTER TABLE `t_location_file_associations` ADD CONSTRAINT `fkhtvits97qtdn3ocj1kgik44j` FOREIGN KEY (`id_file`) REFERENCES `file` (`id`) ON DELETE CASCADE;

ALTER TABLE `t_location_file_associations` ADD CONSTRAINT `fkpxrshqnllftrn323eubyaqqhq` FOREIGN KEY (`id_location`) REFERENCES `location` (`id`) ON DELETE CASCADE;

ALTER TABLE `t_request_file_associations` ADD CONSTRAINT `fki27xearjt3x6ralbb0of1jmmd` FOREIGN KEY (`id_request`) REFERENCES `request` (`id`) ON DELETE CASCADE;

ALTER TABLE `t_request_file_associations` ADD CONSTRAINT `fklrujxbod3pgniljml93urvthe` FOREIGN KEY (`id_file`) REFERENCES `file` (`id`) ON DELETE CASCADE;

ALTER TABLE `t_asset_customer_associations` ADD CONSTRAINT `fkil356y7ax7m8tqfryd4p8pe6o` FOREIGN KEY (`id_asset`) REFERENCES `asset` (`id`) ON DELETE CASCADE;

ALTER TABLE `t_asset_customer_associations` ADD CONSTRAINT `fkk6oovqj8yvfaxueq7pdjm9rj6` FOREIGN KEY (`id_customer`) REFERENCES `customer` (`id`) ON DELETE CASCADE;

ALTER TABLE `t_asset_user_associations` ADD CONSTRAINT `fkjgn04l4eish0drw94bb50lrk3` FOREIGN KEY (`id_user`) REFERENCES `own_user` (`id`) ON DELETE CASCADE;

ALTER TABLE `t_asset_user_associations` ADD CONSTRAINT `fktelhjpyo61q5q6ex771eug6y` FOREIGN KEY (`id_asset`) REFERENCES `asset` (`id`) ON DELETE CASCADE;

ALTER TABLE `t_location_team_associations` ADD CONSTRAINT `fkljl6bq0u1fc9qgyj3ry1pfu1n` FOREIGN KEY (`id_location`) REFERENCES `location` (`id`) ON DELETE CASCADE;

ALTER TABLE `t_location_team_associations` ADD CONSTRAINT `fks4ryynu0uwcxtffdq28y54bbr` FOREIGN KEY (`id_team`) REFERENCES `team` (`id`) ON DELETE CASCADE;

ALTER TABLE `t_asset_part_associations` ADD CONSTRAINT `fklok6yse9itekmdxni6i1f58ys` FOREIGN KEY (`id_asset`) REFERENCES `asset` (`id`) ON DELETE CASCADE;

ALTER TABLE `t_asset_part_associations` ADD CONSTRAINT `fkno24a5qbnjjn9xl0x2goppspd` FOREIGN KEY (`id_part`) REFERENCES `part` (`id`) ON DELETE CASCADE;

ALTER TABLE `t_part_file_associations` ADD CONSTRAINT `fkmjt71i7hevju3uswyyumdgttb` FOREIGN KEY (`id_part`) REFERENCES `part` (`id`) ON DELETE CASCADE;

ALTER TABLE `t_part_file_associations` ADD CONSTRAINT `fkqny8ad10cc9oam3yrfhenfthp` FOREIGN KEY (`id_file`) REFERENCES `file` (`id`) ON DELETE CASCADE;

ALTER TABLE `t_asset_vendor_associations` ADD CONSTRAINT `fkpgtou9e97sr7cmfyxfp1hp9sb` FOREIGN KEY (`id_asset`) REFERENCES `asset` (`id`) ON DELETE CASCADE;

ALTER TABLE `t_asset_vendor_associations` ADD CONSTRAINT `fkrqd179yspkkgkqn96oggb7lhp` FOREIGN KEY (`id_vendor`) REFERENCES `vendor` (`id`) ON DELETE CASCADE;

ALTER TABLE `push_notification_token` ADD CONSTRAINT `fkrv32gwhy1ktdvrlfeg2y1pcqo` FOREIGN KEY (`user_id`) REFERENCES `own_user` (`id`) ON DELETE CASCADE;

ALTER TABLE `workflow_secondary_conditions` ADD CONSTRAINT `fkghlfm1gckeadderktdefpo66s` FOREIGN KEY (`workflow_id`) REFERENCES `workflow` (`id`) ON DELETE CASCADE;

ALTER TABLE `workflow_secondary_conditions` ADD CONSTRAINT `fkshydsqbgheutdqrqsol5h9e0p` FOREIGN KEY (`secondary_conditions_id`) REFERENCES `workflow_condition` (`id`) ON DELETE CASCADE;

ALTER TABLE `checklist` ADD CONSTRAINT `fkkffakgosiaa46j512a564wkpd` FOREIGN KEY (`company_settings_id`) REFERENCES `company_settings` (`id`) ON DELETE CASCADE;

ALTER TABLE `company_settings` ADD CONSTRAINT `fk5bl8j09pkd2jw3tpg4nlx8x9g` FOREIGN KEY (`work_order_request_configuration_id`) REFERENCES `work_order_request_configuration` (`id`) ON DELETE SET NULL;

ALTER TABLE `company_settings` ADD CONSTRAINT `fk9lv45cb6urv0o8r8sq1exw2rt` FOREIGN KEY (`general_preferences_id`) REFERENCES `general_preferences` (`id`) ON DELETE SET NULL;

ALTER TABLE `company_settings` ADD CONSTRAINT `fk_company_settings_ui_configuration` FOREIGN KEY (`ui_configuration_id`) REFERENCES `ui_configuration` (`id`) ON DELETE CASCADE;

ALTER TABLE `company_settings` ADD CONSTRAINT `fklr3cfk7ltvoc41wylou0p9llc` FOREIGN KEY (`work_order_configuration_id`) REFERENCES `work_order_configuration` (`id`) ON DELETE SET NULL;

ALTER TABLE `company_settings` ADD CONSTRAINT `fkokimhprb4eh1c0d48brd1fdaa` FOREIGN KEY (`company_id`) REFERENCES `company` (`id`) ON DELETE CASCADE;

ALTER TABLE `company` ADD CONSTRAINT `fkd2u2jho0d84whs0ryeg9ixs5t` FOREIGN KEY (`logo_id`) REFERENCES `file` (`id`) ON DELETE SET NULL;

ALTER TABLE `company` ADD CONSTRAINT `fkfv376ws8n2akfbxvagtxbkiu2` FOREIGN KEY (`company_settings_id`) REFERENCES `company_settings` (`id`) ON DELETE CASCADE;

ALTER TABLE `company` ADD CONSTRAINT `fkr5eqioud6jn02js440qjt6mxb` FOREIGN KEY (`subscription_id`) REFERENCES `subscription` (`id`) ON DELETE SET NULL;

ALTER TABLE `asset_category` ADD CONSTRAINT `fklj3296bvmjcd0xlho5feip44a` FOREIGN KEY (`company_settings_id`) REFERENCES `company_settings` (`id`) ON DELETE CASCADE;

ALTER TABLE `asset_downtime` ADD CONSTRAINT `fkrbg55bho0cxaorbbt7jwskrpo` FOREIGN KEY (`asset_id`) REFERENCES `asset` (`id`) ON DELETE CASCADE;

ALTER TABLE `asset_downtime` ADD CONSTRAINT `fktm1akp965a93k4gdxg1st9rfi` FOREIGN KEY (`company_id`) REFERENCES `company` (`id`) ON DELETE CASCADE;

ALTER TABLE `additional_cost` ADD CONSTRAINT `fk3tboe213wgtlwl476ci6ue5ia` FOREIGN KEY (`work_order_id`) REFERENCES `work_order` (`id`) ON DELETE CASCADE;

ALTER TABLE `additional_cost` ADD CONSTRAINT `fkhw3t1120mikcf9ajl5tvqb4le` FOREIGN KEY (`category_id`) REFERENCES `cost_category` (`id`) ON DELETE SET NULL;

ALTER TABLE `additional_cost` ADD CONSTRAINT `fkov7r0agrfyv2u9v67rd4e4hxf` FOREIGN KEY (`assigned_to_id`) REFERENCES `own_user` (`id`) ON DELETE SET NULL;

ALTER TABLE `cost_category` ADD CONSTRAINT `fkkbgeqfdaesfnsrbf0vxfdho6t` FOREIGN KEY (`company_settings_id`) REFERENCES `company_settings` (`id`) ON DELETE CASCADE;

ALTER TABLE `floor_plan` ADD CONSTRAINT `fkfg2lm01gcc8gwo3kkm1l64vc0` FOREIGN KEY (`image_id`) REFERENCES `file` (`id`) ON DELETE SET NULL;

ALTER TABLE `floor_plan` ADD CONSTRAINT `fkidx35wj5iwhfi5xrcs9jdsr2d` FOREIGN KEY (`location_id`) REFERENCES `location` (`id`) ON DELETE CASCADE;

ALTER TABLE `deprecation` ADD CONSTRAINT `fkfcqj01lp3ibnkgsmym76tx1hh` FOREIGN KEY (`company_id`) REFERENCES `company` (`id`) ON DELETE CASCADE;

ALTER TABLE `customer` ADD CONSTRAINT `fk86m32arbbnl01mtk7qjeqi0mu` FOREIGN KEY (`billing_currency_id`) REFERENCES `currency` (`id`) ON DELETE SET NULL;

ALTER TABLE `customer` ADD CONSTRAINT `fkcc6lvs1hfb70cc5rjbyq0m8is` FOREIGN KEY (`company_id`) REFERENCES `company` (`id`) ON DELETE CASCADE;

ALTER TABLE `part_preventive_maintenances` ADD CONSTRAINT `fk5cmcx8pexxynebufh9lh49vl6` FOREIGN KEY (`preventive_maintenances_id`) REFERENCES `preventive_maintenance` (`id`) ON DELETE CASCADE;

ALTER TABLE `part_preventive_maintenances` ADD CONSTRAINT `fkcec0nx6ooubsuiwcwg23jod93` FOREIGN KEY (`part_id`) REFERENCES `part` (`id`) ON DELETE CASCADE;

ALTER TABLE `purchase_order` ADD CONSTRAINT `fk20jcn7pw6hvx0uo0sh4y1d9xv` FOREIGN KEY (`vendor_id`) REFERENCES `vendor` (`id`) ON DELETE SET NULL;

ALTER TABLE `purchase_order` ADD CONSTRAINT `fk29o4jh7n4ttmsfh50ulu11hjg` FOREIGN KEY (`category_id`) REFERENCES `purchase_order_category` (`id`) ON DELETE SET NULL;

ALTER TABLE `purchase_order` ADD CONSTRAINT `fknhrkwmvmqly4yq8isdbsbs6mc` FOREIGN KEY (`company_id`) REFERENCES `company` (`id`) ON DELETE CASCADE;

ALTER TABLE `request_customers` ADD CONSTRAINT `fk676vq0swlk6qsrpecgud7rdww` FOREIGN KEY (`customers_id`) REFERENCES `customer` (`id`) ON DELETE CASCADE;

ALTER TABLE `request_customers` ADD CONSTRAINT `fklbeh6vh70b2pqoj6lri2ua4fk` FOREIGN KEY (`request_id`) REFERENCES `request` (`id`) ON DELETE CASCADE;

ALTER TABLE `multi_parts` ADD CONSTRAINT `fkam5yy13545on89ahiokjtb59q` FOREIGN KEY (`company_id`) REFERENCES `company` (`id`) ON DELETE CASCADE;

ALTER TABLE `preventive_maintenance_files` ADD CONSTRAINT `fk8mtccux67q9rtp914y4n7jvom` FOREIGN KEY (`files_id`) REFERENCES `file` (`id`) ON DELETE CASCADE;

ALTER TABLE `preventive_maintenance_files` ADD CONSTRAINT `fkqhoc4ma9gysgmv2445yitvs8f` FOREIGN KEY (`preventive_maintenance_id`) REFERENCES `preventive_maintenance` (`id`) ON DELETE CASCADE;

ALTER TABLE `preventive_maintenance_assigned_to` ADD CONSTRAINT `fk8s7h4xe4shx1myrlnum9wj23h` FOREIGN KEY (`preventive_maintenance_id`) REFERENCES `preventive_maintenance` (`id`) ON DELETE CASCADE;

ALTER TABLE `preventive_maintenance_assigned_to` ADD CONSTRAINT `fkjqfkojt6e6my5qxjgirsg6v8` FOREIGN KEY (`assigned_to_id`) REFERENCES `own_user` (`id`) ON DELETE CASCADE;

ALTER TABLE `preventive_maintenance` ADD CONSTRAINT `fk2r1291umxjxrddelq96v3pl4n` FOREIGN KEY (`asset_id`) REFERENCES `asset` (`id`) ON DELETE SET NULL;

ALTER TABLE `preventive_maintenance` ADD CONSTRAINT `fk3a3u3u7kqwtapl3lskjayyyrl` FOREIGN KEY (`schedule_id`) REFERENCES `schedule` (`id`) ON DELETE SET NULL;

ALTER TABLE `preventive_maintenance` ADD CONSTRAINT `fk53hdmxmgd3fjb2fjsh43d1irf` FOREIGN KEY (`location_id`) REFERENCES `location` (`id`) ON DELETE SET NULL;

ALTER TABLE `preventive_maintenance` ADD CONSTRAINT `fk5uad3bfd5192xkt6u5lx1lhjw` FOREIGN KEY (`image_id`) REFERENCES `file` (`id`) ON DELETE SET NULL;

ALTER TABLE `preventive_maintenance` ADD CONSTRAINT `fkcp1qytc19odw5ngu7y4lx79me` FOREIGN KEY (`team_id`) REFERENCES `team` (`id`) ON DELETE SET NULL;

ALTER TABLE `preventive_maintenance` ADD CONSTRAINT `fkibhc2hvq2hfbom3rwxu7pfxw0` FOREIGN KEY (`company_id`) REFERENCES `company` (`id`) ON DELETE CASCADE;

ALTER TABLE `preventive_maintenance` ADD CONSTRAINT `fkp1wj9d43qdhi19swp3ab00r4e` FOREIGN KEY (`category_id`) REFERENCES `work_order_category` (`id`) ON DELETE SET NULL;

ALTER TABLE `preventive_maintenance` ADD CONSTRAINT `fkt6xom1a5ksujm9l99daervnjw` FOREIGN KEY (`primary_user_id`) REFERENCES `own_user` (`id`) ON DELETE SET NULL;

ALTER TABLE `own_user_work_orders` ADD CONSTRAINT `fk9shb970menrny3c08rd8hqwd5` FOREIGN KEY (`own_user_id`) REFERENCES `own_user` (`id`) ON DELETE CASCADE;

ALTER TABLE `own_user_work_orders` ADD CONSTRAINT `fkcgnpjx8ahn3ptxpl9ucot819u` FOREIGN KEY (`work_orders_id`) REFERENCES `work_order` (`id`) ON DELETE CASCADE;

ALTER TABLE `own_user_preventive_maintenances` ADD CONSTRAINT `fk9ttebk37s6yxteght7367u6hl` FOREIGN KEY (`own_user_id`) REFERENCES `own_user` (`id`) ON DELETE CASCADE;

ALTER TABLE `own_user_preventive_maintenances` ADD CONSTRAINT `fkcppm5srhdsuc9di027fiv62aa` FOREIGN KEY (`preventive_maintenances_id`) REFERENCES `preventive_maintenance` (`id`) ON DELETE CASCADE;

ALTER TABLE `request_files` ADD CONSTRAINT `fkd3vswlusjseoo7lpwb0697w2o` FOREIGN KEY (`request_id`) REFERENCES `request` (`id`) ON DELETE CASCADE;

ALTER TABLE `request_files` ADD CONSTRAINT `fke28fh1hvbl1e8jww275n3b1w0` FOREIGN KEY (`files_id`) REFERENCES `file` (`id`) ON DELETE CASCADE;

ALTER TABLE `preventive_maintenance_customers` ADD CONSTRAINT `fkjihm0pei7bll8d9oim1v4edjo` FOREIGN KEY (`preventive_maintenance_id`) REFERENCES `preventive_maintenance` (`id`) ON DELETE CASCADE;

ALTER TABLE `preventive_maintenance_customers` ADD CONSTRAINT `fkm4lsl8y9ttb1jvliu0af15mh3` FOREIGN KEY (`customers_id`) REFERENCES `customer` (`id`) ON DELETE CASCADE;

ALTER TABLE `meter` ADD CONSTRAINT `fk578asjaixmqs7pn07tho2i1pc` FOREIGN KEY (`company_id`) REFERENCES `company` (`id`) ON DELETE CASCADE;

ALTER TABLE `meter` ADD CONSTRAINT `fk7sr43v36jpwvqhwvuk0o8ob0w` FOREIGN KEY (`image_id`) REFERENCES `file` (`id`) ON DELETE SET NULL;

ALTER TABLE `meter` ADD CONSTRAINT `fk8jgoj4oi6b9sjqeuh9v592a6q` FOREIGN KEY (`location_id`) REFERENCES `location` (`id`) ON DELETE SET NULL;

ALTER TABLE `meter` ADD CONSTRAINT `fkoid61jgpxbr04np7rop8pag13` FOREIGN KEY (`meter_category_id`) REFERENCES `meter_category` (`id`) ON DELETE SET NULL;

ALTER TABLE `meter` ADD CONSTRAINT `fkswqod52v3556tp6obl9c8lvxi` FOREIGN KEY (`asset_id`) REFERENCES `asset` (`id`) ON DELETE CASCADE;

ALTER TABLE `relation` ADD CONSTRAINT `fkfi6xl41fol251r2391ypx9ga5` FOREIGN KEY (`parent_id`) REFERENCES `work_order` (`id`) ON DELETE CASCADE;

ALTER TABLE `relation` ADD CONSTRAINT `fkmn3265pc5poqrn82h1j75if3p` FOREIGN KEY (`child_id`) REFERENCES `work_order` (`id`) ON DELETE CASCADE;

ALTER TABLE `relation` ADD CONSTRAINT `fktka3m8kybt9riu4x4no4vwat0` FOREIGN KEY (`company_id`) REFERENCES `company` (`id`) ON DELETE CASCADE;

ALTER TABLE `reading` ADD CONSTRAINT `fkrk4bo4tq59pfymwvx2jnjvlae` FOREIGN KEY (`meter_id`) REFERENCES `meter` (`id`) ON DELETE CASCADE;

ALTER TABLE `location` ADD CONSTRAINT `fk9s1sco601u8l6ptdxf432h4kb` FOREIGN KEY (`parent_location_id`) REFERENCES `location` (`id`) ON DELETE CASCADE;

ALTER TABLE `location` ADD CONSTRAINT `fkbxoiqd2lmvk7c3r3qhfoou2py` FOREIGN KEY (`company_id`) REFERENCES `company` (`id`) ON DELETE CASCADE;

ALTER TABLE `location` ADD CONSTRAINT `fkjuo4g2hd6k6g383v9jr6megvr` FOREIGN KEY (`image_id`) REFERENCES `file` (`id`) ON DELETE SET NULL;

ALTER TABLE `notification` ADD CONSTRAINT `fk99ahq0v75lcyyue9jk2jx01xh` FOREIGN KEY (`user_id`) REFERENCES `own_user` (`id`) ON DELETE SET NULL;

ALTER TABLE `notification` ADD CONSTRAINT `notification_event_company_id_fkey` FOREIGN KEY (`event_company_id`) REFERENCES `company` (`id`) ON DELETE CASCADE;

ALTER TABLE `request` ADD CONSTRAINT `fk3j1uj2uh9af66u9jmfjtvraar` FOREIGN KEY (`image_id`) REFERENCES `file` (`id`) ON DELETE SET NULL;

ALTER TABLE `request` ADD CONSTRAINT `fk5w6no964jn5lpg4tpeugv8xx7` FOREIGN KEY (`company_id`) REFERENCES `company` (`id`) ON DELETE CASCADE;

ALTER TABLE `request` ADD CONSTRAINT `fk_request_audio_description` FOREIGN KEY (`audio_description_id`) REFERENCES `file` (`id`) ON DELETE CASCADE;

ALTER TABLE `request` ADD CONSTRAINT `fk_request_request_portal` FOREIGN KEY (`request_portal_id`) REFERENCES `request_portal` (`id`) ON DELETE SET NULL;

ALTER TABLE `request` ADD CONSTRAINT `fkfweq9owhxrrcsowk9w12qgmrb` FOREIGN KEY (`category_id`) REFERENCES `work_order_category` (`id`) ON DELETE SET NULL;

ALTER TABLE `request` ADD CONSTRAINT `fkj7y0rwrqngdydkkdcohwisqq6` FOREIGN KEY (`location_id`) REFERENCES `location` (`id`) ON DELETE SET NULL;

ALTER TABLE `request` ADD CONSTRAINT `fkml2rlcjiurmq5rjyw1gmsstf6` FOREIGN KEY (`asset_id`) REFERENCES `asset` (`id`) ON DELETE SET NULL;

ALTER TABLE `request` ADD CONSTRAINT `fkn6hy6udqudy40u4cgtwvwtp9l` FOREIGN KEY (`primary_user_id`) REFERENCES `own_user` (`id`) ON DELETE SET NULL;

ALTER TABLE `request` ADD CONSTRAINT `fkoo6uxlauwwyxfohx3lver2t0x` FOREIGN KEY (`work_order_id`) REFERENCES `work_order` (`id`) ON DELETE SET NULL;

ALTER TABLE `request` ADD CONSTRAINT `fktb79vm0l79yhsnqtllqgf97nl` FOREIGN KEY (`team_id`) REFERENCES `team` (`id`) ON DELETE SET NULL;

ALTER TABLE `part` ADD CONSTRAINT `fk_part_category` FOREIGN KEY (`category_id`) REFERENCES `part_category` (`id`) ON DELETE CASCADE;

ALTER TABLE `part` ADD CONSTRAINT `fkeh8vu21l8gci666ew34wnfgb9` FOREIGN KEY (`image_id`) REFERENCES `file` (`id`) ON DELETE SET NULL;

ALTER TABLE `part` ADD CONSTRAINT `fkthru5degka2ss0r44n7uphvoj` FOREIGN KEY (`company_id`) REFERENCES `company` (`id`) ON DELETE CASCADE;

ALTER TABLE `work_order_assigned_to` ADD CONSTRAINT `fkmfhu1adi2mobtmdpsprmuo267` FOREIGN KEY (`work_order_id`) REFERENCES `work_order` (`id`) ON DELETE CASCADE;

ALTER TABLE `work_order_assigned_to` ADD CONSTRAINT `fks2pjdwafjgekyxtwj4f82qn4q` FOREIGN KEY (`assigned_to_id`) REFERENCES `own_user` (`id`) ON DELETE CASCADE;

ALTER TABLE `work_order_category` ADD CONSTRAINT `fk29i1j8j23y946usq1eth5qf6a` FOREIGN KEY (`company_settings_id`) REFERENCES `company_settings` (`id`) ON DELETE CASCADE;

ALTER TABLE `work_order` ADD CONSTRAINT `fk3rui6pnm8in4f41ck17h595kg` FOREIGN KEY (`image_id`) REFERENCES `file` (`id`) ON DELETE SET NULL;

ALTER TABLE `work_order` ADD CONSTRAINT `fk4b1ej5p5dfnnqb68te2pd9hx4` FOREIGN KEY (`category_id`) REFERENCES `work_order_category` (`id`) ON DELETE SET NULL;

ALTER TABLE `work_order` ADD CONSTRAINT `fk89batrcg5n0tvewqhad27e8oa` FOREIGN KEY (`company_id`) REFERENCES `company` (`id`) ON DELETE CASCADE;

ALTER TABLE `work_order` ADD CONSTRAINT `fkac4vsg728gaycwq1w9o77uw3c` FOREIGN KEY (`completed_by_id`) REFERENCES `own_user` (`id`) ON DELETE SET NULL;

ALTER TABLE `work_order` ADD CONSTRAINT `fkajaqh96l0dquvxdfw8fj77241` FOREIGN KEY (`parent_preventive_maintenance_id`) REFERENCES `preventive_maintenance` (`id`) ON DELETE SET NULL;

ALTER TABLE `work_order` ADD CONSTRAINT `fkauql9ts1kbmwftgtrjbdys6a3` FOREIGN KEY (`team_id`) REFERENCES `team` (`id`) ON DELETE SET NULL;

ALTER TABLE `work_order` ADD CONSTRAINT `fkdbtdl51i4yup1hs6ek3ecqros` FOREIGN KEY (`location_id`) REFERENCES `location` (`id`) ON DELETE SET NULL;

ALTER TABLE `work_order` ADD CONSTRAINT `fknixhvhyqdk11xntyainal50tf` FOREIGN KEY (`primary_user_id`) REFERENCES `own_user` (`id`) ON DELETE SET NULL;

ALTER TABLE `work_order` ADD CONSTRAINT `fko87wf45c2qbyn4jhgjv8s4s5d` FOREIGN KEY (`parent_request_id`) REFERENCES `request` (`id`) ON DELETE SET NULL;

ALTER TABLE `work_order` ADD CONSTRAINT `fkp4hbmaa9hcp6myymsfyxg6fo2` FOREIGN KEY (`asset_id`) REFERENCES `asset` (`id`) ON DELETE SET NULL;

ALTER TABLE `work_order` ADD CONSTRAINT `fks33w3o0htu3jkkj4r40w1plvs` FOREIGN KEY (`signature_id`) REFERENCES `file` (`id`) ON DELETE SET NULL;

ALTER TABLE `vendor` ADD CONSTRAINT `fksfx6gf0an0bo7kwscuhi1hoya` FOREIGN KEY (`company_id`) REFERENCES `company` (`id`) ON DELETE CASCADE;

ALTER TABLE `task_base` ADD CONSTRAINT `fkd4mq9o4l0yvmwa8n913k8xmei` FOREIGN KEY (`company_id`) REFERENCES `company` (`id`) ON DELETE CASCADE;

ALTER TABLE `task_base` ADD CONSTRAINT `fkfcyjas01bhns54mn9uxk3xqx8` FOREIGN KEY (`asset_id`) REFERENCES `asset` (`id`) ON DELETE CASCADE;

ALTER TABLE `task_base` ADD CONSTRAINT `fkgtij68ko3kykqk1od01ahoudg` FOREIGN KEY (`meter_id`) REFERENCES `meter` (`id`) ON DELETE CASCADE;

ALTER TABLE `task_base` ADD CONSTRAINT `fkjuw0e6wckoo1tqast1m6baj9f` FOREIGN KEY (`user_id`) REFERENCES `own_user` (`id`) ON DELETE SET NULL;

ALTER TABLE `task_option` ADD CONSTRAINT `fkepxf7nnly1w2n7w9c4wj9w68m` FOREIGN KEY (`company_id`) REFERENCES `company` (`id`) ON DELETE CASCADE;

ALTER TABLE `task_option` ADD CONSTRAINT `fkko048mkiejn0ikwy5t4rqwf7o` FOREIGN KEY (`task_base_id`) REFERENCES `task_base` (`id`) ON DELETE SET NULL;

ALTER TABLE `user_invitation` ADD CONSTRAINT `fkf77hi4ixclj9edbkfheps1sv9` FOREIGN KEY (`role_id`) REFERENCES `role` (`id`) ON DELETE CASCADE;

ALTER TABLE `task` ADD CONSTRAINT `fk40yui5s4d1qatg5yv31py6fr2` FOREIGN KEY (`task_base_id`) REFERENCES `task_base` (`id`) ON DELETE CASCADE;

ALTER TABLE `task` ADD CONSTRAINT `fkbopfrnwvm6sr4iq4y1j8xa36e` FOREIGN KEY (`work_order_id`) REFERENCES `work_order` (`id`) ON DELETE CASCADE;

ALTER TABLE `task` ADD CONSTRAINT `fkkovhsjug063l45ggbgdfxp21s` FOREIGN KEY (`company_id`) REFERENCES `company` (`id`) ON DELETE CASCADE;

ALTER TABLE `task` ADD CONSTRAINT `fkqdyjp9iw7kxtukq2vlqrgtj16` FOREIGN KEY (`preventive_maintenance_id`) REFERENCES `preventive_maintenance` (`id`) ON DELETE CASCADE;

ALTER TABLE `schedule` ADD CONSTRAINT `fkslpq17o786xhp0jbk869hvdhy` FOREIGN KEY (`preventive_maintenance_id`) REFERENCES `preventive_maintenance` (`id`) ON DELETE CASCADE;

ALTER TABLE `role_create_permissions` ADD CONSTRAINT `fktncs84qs8gmbgb3qsula45o4r` FOREIGN KEY (`role_id`) REFERENCES `role` (`id`) ON DELETE CASCADE;

ALTER TABLE `time_category` ADD CONSTRAINT `fk87e9ddkvokl4g0d787clik15c` FOREIGN KEY (`company_settings_id`) REFERENCES `company_settings` (`id`) ON DELETE CASCADE;

ALTER TABLE `subscription` ADD CONSTRAINT `fkhn8hnxbdoi29nb4m7ojkocqfm` FOREIGN KEY (`subscription_plan_id`) REFERENCES `subscription_plan` (`id`) ON DELETE CASCADE;

ALTER TABLE `role` ADD CONSTRAINT `fk5vkif568seghq1gpej2f64dne` FOREIGN KEY (`company_settings_id`) REFERENCES `company_settings` (`id`) ON DELETE CASCADE;

ALTER TABLE `role_edit_other_permissions` ADD CONSTRAINT `fk7rf93e2irl5320ogujwr8wcgt` FOREIGN KEY (`role_id`) REFERENCES `role` (`id`) ON DELETE CASCADE;

ALTER TABLE `role_view_permissions` ADD CONSTRAINT `fkdcn9sty6hgjhdocywpdyqbl8s` FOREIGN KEY (`role_id`) REFERENCES `role` (`id`) ON DELETE CASCADE;

ALTER TABLE `role_delete_other_permissions` ADD CONSTRAINT `fk3cjdng98d8kdkyhqniaatb1nq` FOREIGN KEY (`role_id`) REFERENCES `role` (`id`) ON DELETE CASCADE;

ALTER TABLE `subscription_plan_features` ADD CONSTRAINT `fkt9uih9cwfu9enes7yueekd2rx` FOREIGN KEY (`subscription_plan_id`) REFERENCES `subscription_plan` (`id`) ON DELETE CASCADE;

ALTER TABLE `work_order_meter_trigger_customers` ADD CONSTRAINT `fk6tm1ncq9jkxakgv2imdbwnntj` FOREIGN KEY (`customers_id`) REFERENCES `customer` (`id`) ON DELETE CASCADE;

ALTER TABLE `work_order_meter_trigger_customers` ADD CONSTRAINT `fkkvfp8se58k6wyyt8ctgv4r3f0` FOREIGN KEY (`work_order_meter_trigger_id`) REFERENCES `work_order_meter_trigger` (`id`) ON DELETE CASCADE;

ALTER TABLE `team` ADD CONSTRAINT `fkq7becd703i1w0ry1jxy15qxid` FOREIGN KEY (`company_id`) REFERENCES `company` (`id`) ON DELETE CASCADE;

ALTER TABLE `work_order_configuration` ADD CONSTRAINT `fk3kl0h9w3re1oi0vl5k8rx1ojh` FOREIGN KEY (`company_settings_id`) REFERENCES `company_settings` (`id`) ON DELETE CASCADE;

ALTER TABLE `work_order_meter_trigger_assigned_to` ADD CONSTRAINT `fk42orqs7y05ghgkis54tathjv` FOREIGN KEY (`work_order_meter_trigger_id`) REFERENCES `work_order_meter_trigger` (`id`) ON DELETE CASCADE;

ALTER TABLE `work_order_meter_trigger_assigned_to` ADD CONSTRAINT `fkbjgihoivnrlo4tnjf3mlb93e8` FOREIGN KEY (`assigned_to_id`) REFERENCES `own_user` (`id`) ON DELETE CASCADE;

ALTER TABLE `work_order_meter_trigger_files` ADD CONSTRAINT `fk48ipt6j68d2prhkoeo6tv7toc` FOREIGN KEY (`files_id`) REFERENCES `file` (`id`) ON DELETE CASCADE;

ALTER TABLE `work_order_meter_trigger_files` ADD CONSTRAINT `fkol2wwydmtktqbnc7lvv23r5jy` FOREIGN KEY (`work_order_meter_trigger_id`) REFERENCES `work_order_meter_trigger` (`id`) ON DELETE CASCADE;

ALTER TABLE `work_order_request_configuration` ADD CONSTRAINT `fkow32v30lf3pnb8wnvp5lqst0w` FOREIGN KEY (`company_settings_id`) REFERENCES `company_settings` (`id`) ON DELETE CASCADE;

ALTER TABLE `role_view_other_permissions` ADD CONSTRAINT `fk2o0cjn2xwd4jkb5bsukxlnod8` FOREIGN KEY (`role_id`) REFERENCES `role` (`id`) ON DELETE CASCADE;

ALTER TABLE `work_order_customers` ADD CONSTRAINT `fk5c0105hu4penp7v2u9tmjg50w` FOREIGN KEY (`work_order_id`) REFERENCES `work_order` (`id`) ON DELETE CASCADE;

ALTER TABLE `work_order_customers` ADD CONSTRAINT `fkglrmndromgkolt5unqykpu45u` FOREIGN KEY (`customers_id`) REFERENCES `customer` (`id`) ON DELETE CASCADE;

ALTER TABLE `work_order_files` ADD CONSTRAINT `fkccol3khq2jntfddtvcfv8hk56` FOREIGN KEY (`files_id`) REFERENCES `file` (`id`) ON DELETE CASCADE;

ALTER TABLE `work_order_files` ADD CONSTRAINT `fkdoggd73pxsy9naoq00g3epsg1` FOREIGN KEY (`work_order_id`) REFERENCES `work_order` (`id`) ON DELETE CASCADE;

ALTER TABLE `request_assigned_to` ADD CONSTRAINT `fkeg8mmectqp7vcxgxw7k64wt1h` FOREIGN KEY (`request_id`) REFERENCES `request` (`id`) ON DELETE CASCADE;

ALTER TABLE `request_assigned_to` ADD CONSTRAINT `fkqlbo8p3wkhw4ddewc0cx4d28q` FOREIGN KEY (`assigned_to_id`) REFERENCES `own_user` (`id`) ON DELETE CASCADE;

ALTER TABLE `work_order_history` ADD CONSTRAINT `fkggkgvrsfxoevabt4igrse99m3` FOREIGN KEY (`user_id`) REFERENCES `own_user` (`id`) ON DELETE CASCADE;

ALTER TABLE `work_order_history` ADD CONSTRAINT `fkqxdc4ix6tovtwyr0on2f59hh4` FOREIGN KEY (`work_order_id`) REFERENCES `work_order` (`id`) ON DELETE CASCADE;

ALTER TABLE `super_account_relation` ADD CONSTRAINT `fk_super_account_relation_child_user` FOREIGN KEY (`child_user_id`) REFERENCES `own_user` (`id`) ON DELETE CASCADE;

ALTER TABLE `super_account_relation` ADD CONSTRAINT `fk_super_account_relation_super_user` FOREIGN KEY (`super_user_id`) REFERENCES `own_user` (`id`) ON DELETE CASCADE;

ALTER TABLE `purchase_order_category` ADD CONSTRAINT `fkc7wlc4jicpk0243alj8biab6v` FOREIGN KEY (`company_settings_id`) REFERENCES `company_settings` (`id`) ON DELETE CASCADE;

ALTER TABLE `work_order_meter_trigger` ADD CONSTRAINT `fk87doqr843tj4vmxarqujm3sgy` FOREIGN KEY (`meter_id`) REFERENCES `meter` (`id`) ON DELETE CASCADE;

ALTER TABLE `work_order_meter_trigger` ADD CONSTRAINT `fk8ldob1vt7bg8dexi3p6x86sfo` FOREIGN KEY (`team_id`) REFERENCES `team` (`id`) ON DELETE SET NULL;

ALTER TABLE `work_order_meter_trigger` ADD CONSTRAINT `fkbdhu8fagmbqqtyf07h10lap9o` FOREIGN KEY (`image_id`) REFERENCES `file` (`id`) ON DELETE SET NULL;

ALTER TABLE `work_order_meter_trigger` ADD CONSTRAINT `fkdkxi8xmqmo48y8xtml1860nbc` FOREIGN KEY (`asset_id`) REFERENCES `asset` (`id`) ON DELETE SET NULL;

ALTER TABLE `work_order_meter_trigger` ADD CONSTRAINT `fkmdb3avajahyo8mc6wi38ls8xi` FOREIGN KEY (`location_id`) REFERENCES `location` (`id`) ON DELETE SET NULL;

ALTER TABLE `work_order_meter_trigger` ADD CONSTRAINT `fkmlswjhdj6yvdr0gxttjbivans` FOREIGN KEY (`company_id`) REFERENCES `company` (`id`) ON DELETE CASCADE;

ALTER TABLE `work_order_meter_trigger` ADD CONSTRAINT `fkrle6bql4ubcd8vjmqb19aukwl` FOREIGN KEY (`primary_user_id`) REFERENCES `own_user` (`id`) ON DELETE SET NULL;

ALTER TABLE `work_order_meter_trigger` ADD CONSTRAINT `fkt8pkfck219uufrehqu9k8ekh5` FOREIGN KEY (`category_id`) REFERENCES `work_order_category` (`id`) ON DELETE SET NULL;

ALTER TABLE `asset` ADD CONSTRAINT `fk6qu6wqhpppa2w4y09l3tt4q56` FOREIGN KEY (`parent_asset_id`) REFERENCES `asset` (`id`) ON DELETE CASCADE;

ALTER TABLE `asset` ADD CONSTRAINT `fkas2sx5fk8xkk8l6mppu2tmlvc` FOREIGN KEY (`deprecation_id`) REFERENCES `deprecation` (`id`) ON DELETE SET NULL;

ALTER TABLE `asset` ADD CONSTRAINT `fkbnbckrvy4gleqsugkauihx7d7` FOREIGN KEY (`company_id`) REFERENCES `company` (`id`) ON DELETE CASCADE;

ALTER TABLE `asset` ADD CONSTRAINT `fkhh76eg71adtmdagspxgbnmjd5` FOREIGN KEY (`image_id`) REFERENCES `file` (`id`) ON DELETE SET NULL;

ALTER TABLE `asset` ADD CONSTRAINT `fkhw44q3ymkd329b8b6mvqxatsh` FOREIGN KEY (`category_id`) REFERENCES `asset_category` (`id`) ON DELETE SET NULL;

ALTER TABLE `asset` ADD CONSTRAINT `fkmq4as1d1exvm4mg3ayokdan30` FOREIGN KEY (`primary_user_id`) REFERENCES `own_user` (`id`) ON DELETE SET NULL;

ALTER TABLE `asset` ADD CONSTRAINT `fkoo11h2f4j12wv0axk6d8u1wy0` FOREIGN KEY (`location_id`) REFERENCES `location` (`id`) ON DELETE SET NULL;

ALTER TABLE `ui_configuration` ADD CONSTRAINT `fk_ui_configuration_company_settings` FOREIGN KEY (`company_settings_id`) REFERENCES `company_settings` (`id`) ON DELETE CASCADE;

ALTER TABLE `work_order_aud` ADD CONSTRAINT `FK_work_order_AUD_category_id` FOREIGN KEY (`category_id`) REFERENCES `work_order_category` (`id`) ON DELETE SET NULL;

ALTER TABLE `work_order_aud` ADD CONSTRAINT `FK_work_order_AUD_completedBy_id` FOREIGN KEY (`completed_by_id`) REFERENCES `own_user` (`id`) ON DELETE SET NULL;

ALTER TABLE `work_order_aud` ADD CONSTRAINT `FK_work_order_AUD_image_id` FOREIGN KEY (`image_id`) REFERENCES `file` (`id`) ON DELETE SET NULL;

ALTER TABLE `work_order_aud` ADD CONSTRAINT `FK_work_order_AUD_location_id` FOREIGN KEY (`location_id`) REFERENCES `location` (`id`) ON DELETE SET NULL;

ALTER TABLE `work_order_aud` ADD CONSTRAINT `FK_work_order_AUD_parentPreventiveMaintenance_id` FOREIGN KEY (`parent_preventive_maintenance_id`) REFERENCES `preventive_maintenance` (`id`) ON DELETE SET NULL;

ALTER TABLE `work_order_aud` ADD CONSTRAINT `FK_work_order_AUD_parentRequest_id` FOREIGN KEY (`parent_request_id`) REFERENCES `request` (`id`) ON DELETE SET NULL;

ALTER TABLE `work_order_aud` ADD CONSTRAINT `FK_work_order_AUD_primaryUser_id` FOREIGN KEY (`primary_user_id`) REFERENCES `own_user` (`id`) ON DELETE SET NULL;

ALTER TABLE `work_order_aud` ADD CONSTRAINT `FK_work_order_AUD_signature_id` FOREIGN KEY (`signature_id`) REFERENCES `file` (`id`) ON DELETE SET NULL;

ALTER TABLE `work_order_aud` ADD CONSTRAINT `FK_work_order_AUD_team_id` FOREIGN KEY (`team_id`) REFERENCES `team` (`id`) ON DELETE SET NULL;

ALTER TABLE `subscription_change_request` ADD CONSTRAINT `fk_subscription_change_request_company` FOREIGN KEY (`company_id`) REFERENCES `company` (`id`) ON DELETE CASCADE;

ALTER TABLE `part_category` ADD CONSTRAINT `fk_part_category_company_settings` FOREIGN KEY (`company_settings_id`) REFERENCES `company_settings` (`id`) ON DELETE CASCADE;

ALTER TABLE `meter_category` ADD CONSTRAINT `fk26q7jqesxesg8d65hj8c9f1gv` FOREIGN KEY (`company_settings_id`) REFERENCES `company_settings` (`id`) ON DELETE CASCADE;

ALTER TABLE `custom_sequence` ADD CONSTRAINT `fk_custom_sequence_company` FOREIGN KEY (`company_id`) REFERENCES `company` (`id`) ON DELETE CASCADE;

ALTER TABLE `part_transaction` ADD CONSTRAINT `fkbx2tggt11xc2ofuwui2xsxkg0` FOREIGN KEY (`part_id`) REFERENCES `part` (`id`) ON DELETE CASCADE;

ALTER TABLE `part_transaction` ADD CONSTRAINT `fkib1h4krpiuuk1meixq7qf9g86` FOREIGN KEY (`company_id`) REFERENCES `company` (`id`) ON DELETE CASCADE;

ALTER TABLE `part_transaction` ADD CONSTRAINT `fkj99sa2uououd6s977aqyc0yri` FOREIGN KEY (`work_order_id`) REFERENCES `work_order` (`id`) ON DELETE CASCADE;

ALTER TABLE `QRTZ_TRIGGERS` ADD CONSTRAINT `QRTZ_TRIGGERS_SCHED_NAME_JOB_NAME_JOB_GROUP_FKEY` FOREIGN KEY (`sched_name`,`job_name`,`job_group`) REFERENCES `QRTZ_JOB_DETAILS` (`sched_name`,`job_name`,`job_group`);

ALTER TABLE `schedule_days_of_week` ADD CONSTRAINT `fk_schedule_days_of_week_schedule` FOREIGN KEY (`schedule_id`) REFERENCES `schedule` (`id`) ON DELETE CASCADE;

ALTER TABLE `QRTZ_SIMPLE_TRIGGERS` ADD CONSTRAINT `QRTZ_SIMPLE_TRIGGERS_SCHED_NAME_TRIGGER_NAME_TRIGGER_GROUP_FKEY` FOREIGN KEY (`sched_name`,`trigger_name`,`trigger_group`) REFERENCES `QRTZ_TRIGGERS` (`sched_name`,`trigger_name`,`trigger_group`);

ALTER TABLE `QRTZ_CRON_TRIGGERS` ADD CONSTRAINT `QRTZ_CRON_TRIGGERS_SCHED_NAME_TRIGGER_NAME_TRIGGER_GROUP_FKEY` FOREIGN KEY (`sched_name`,`trigger_name`,`trigger_group`) REFERENCES `QRTZ_TRIGGERS` (`sched_name`,`trigger_name`,`trigger_group`);

ALTER TABLE `QRTZ_SIMPROP_TRIGGERS` ADD CONSTRAINT `QRTZ_SIMPROP_TRIGGERS_SCHED_NAME_TRIGGER_NAME_TRIGGER_GROU_FKEY` FOREIGN KEY (`sched_name`,`trigger_name`,`trigger_group`) REFERENCES `QRTZ_TRIGGERS` (`sched_name`,`trigger_name`,`trigger_group`);

ALTER TABLE `own_user` ADD CONSTRAINT `fk_own_user_app_stats` FOREIGN KEY (`app_stats_id`) REFERENCES `user_app_stats` (`id`) ON DELETE SET NULL;

ALTER TABLE `own_user` ADD CONSTRAINT `fk_own_user_shift_config` FOREIGN KEY (`shift_configuration_id`) REFERENCES `shift_configuration` (`id`) ON DELETE SET NULL;

ALTER TABLE `own_user` ADD CONSTRAINT `fkedmmabpmsf25ut5ak07i1rban` FOREIGN KEY (`company_id`) REFERENCES `company` (`id`) ON DELETE CASCADE;

ALTER TABLE `own_user` ADD CONSTRAINT `fkqwalae8dm1n117q5p2k5xv8mk` FOREIGN KEY (`image_id`) REFERENCES `file` (`id`) ON DELETE SET NULL;

ALTER TABLE `own_user` ADD CONSTRAINT `fkrlvsrdrcmmbtbgeqr8y4j69p4` FOREIGN KEY (`role_id`) REFERENCES `role` (`id`) ON DELETE CASCADE;

ALTER TABLE `own_user` ADD CONSTRAINT `fkssx9vqtrk77wqmtsi1gokyi8l` FOREIGN KEY (`user_settings_id`) REFERENCES `user_settings` (`id`) ON DELETE SET NULL;

ALTER TABLE `own_user` ADD CONSTRAINT `fktmprdfwe8axkp8cobywe2gc1d` FOREIGN KEY (`location_id`) REFERENCES `location` (`id`) ON DELETE SET NULL;

ALTER TABLE `QRTZ_BLOB_TRIGGERS` ADD CONSTRAINT `QRTZ_BLOB_TRIGGERS_SCHED_NAME_TRIGGER_NAME_TRIGGER_GROUP_FKEY` FOREIGN KEY (`sched_name`,`trigger_name`,`trigger_group`) REFERENCES `QRTZ_TRIGGERS` (`sched_name`,`trigger_name`,`trigger_group`);

ALTER TABLE `workflow_condition` ADD CONSTRAINT `fk7mrdl03tbxy73louegqmtff5y` FOREIGN KEY (`team_id`) REFERENCES `team` (`id`) ON DELETE CASCADE;

ALTER TABLE `workflow_condition` ADD CONSTRAINT `fkbn902y2gyu5mrf43spdiw8duo` FOREIGN KEY (`part_id`) REFERENCES `part` (`id`) ON DELETE CASCADE;

ALTER TABLE `workflow_condition` ADD CONSTRAINT `fkexm8hsr4hficvbx66p66asyhs` FOREIGN KEY (`vendor_id`) REFERENCES `vendor` (`id`) ON DELETE CASCADE;

ALTER TABLE `workflow_condition` ADD CONSTRAINT `fkhsrmx7f4fbfh4g1jdo5vray90` FOREIGN KEY (`asset_id`) REFERENCES `asset` (`id`) ON DELETE CASCADE;

ALTER TABLE `workflow_condition` ADD CONSTRAINT `fkjus3y7kh66dyo0ryefx02iqx3` FOREIGN KEY (`company_id`) REFERENCES `company` (`id`) ON DELETE CASCADE;

ALTER TABLE `workflow_condition` ADD CONSTRAINT `fkoueqwngifye55uqf2pj5u0257` FOREIGN KEY (`location_id`) REFERENCES `location` (`id`) ON DELETE CASCADE;

ALTER TABLE `workflow_condition` ADD CONSTRAINT `fkr0f6t6jua29he3jhq40ty7wkc` FOREIGN KEY (`purchase_order_category_id`) REFERENCES `purchase_order_category` (`id`) ON DELETE CASCADE;

ALTER TABLE `workflow_condition` ADD CONSTRAINT `fks9hh8bjsvrt9f5od4uyyw0j6c` FOREIGN KEY (`user_id`) REFERENCES `own_user` (`id`) ON DELETE CASCADE;

ALTER TABLE `workflow_condition` ADD CONSTRAINT `fktqo77yorjst58d6j6rovs4yi7` FOREIGN KEY (`work_order_category_id`) REFERENCES `work_order_category` (`id`) ON DELETE CASCADE;

ALTER TABLE `workflow_action` ADD CONSTRAINT `fk6nkenbmcv4vyxw23kwm4ftgaq` FOREIGN KEY (`location_id`) REFERENCES `location` (`id`) ON DELETE CASCADE;

ALTER TABLE `workflow_action` ADD CONSTRAINT `fkbvldf2gcw2fnr9flgnfr76i7i` FOREIGN KEY (`checklist_id`) REFERENCES `checklist` (`id`) ON DELETE CASCADE;

ALTER TABLE `workflow_action` ADD CONSTRAINT `fkd1dqs6sf06v5i8jp26pax02dc` FOREIGN KEY (`company_id`) REFERENCES `company` (`id`) ON DELETE CASCADE;

ALTER TABLE `workflow_action` ADD CONSTRAINT `fkk62qy20rgsjmy2l8jvehrew58` FOREIGN KEY (`purchase_order_category_id`) REFERENCES `purchase_order_category` (`id`) ON DELETE CASCADE;

ALTER TABLE `workflow_action` ADD CONSTRAINT `fklujtc6kod27c7hqhtuvfx5nx6` FOREIGN KEY (`work_order_category_id`) REFERENCES `work_order_category` (`id`) ON DELETE CASCADE;

ALTER TABLE `workflow_action` ADD CONSTRAINT `fkonpcm48v2k8p83xkj6gf5wwip` FOREIGN KEY (`vendor_id`) REFERENCES `vendor` (`id`) ON DELETE CASCADE;

ALTER TABLE `workflow_action` ADD CONSTRAINT `fks2ytj9hm7fm8w9uekixsq2vip` FOREIGN KEY (`user_id`) REFERENCES `own_user` (`id`) ON DELETE SET NULL;

ALTER TABLE `workflow_action` ADD CONSTRAINT `fksc5wshd9hdy2gdfrgnh73r9n2` FOREIGN KEY (`team_id`) REFERENCES `team` (`id`) ON DELETE CASCADE;

ALTER TABLE `workflow_action` ADD CONSTRAINT `fkufu41twy3j9sfswo2tm7nkue` FOREIGN KEY (`asset_id`) REFERENCES `asset` (`id`) ON DELETE CASCADE;

ALTER TABLE `workflow` ADD CONSTRAINT `fk2yopr6sxpui8gj22yt1qlot2g` FOREIGN KEY (`company_id`) REFERENCES `company` (`id`) ON DELETE CASCADE;

ALTER TABLE `workflow` ADD CONSTRAINT `fk5bqe81hkk7jfmavn55a8yvca7` FOREIGN KEY (`action_id`) REFERENCES `workflow_action` (`id`) ON DELETE CASCADE;

ALTER TABLE `field_configuration` ADD CONSTRAINT `fknm0jfo2of1li65idn1qvbbm00` FOREIGN KEY (`work_order_configuration_id`) REFERENCES `work_order_configuration` (`id`) ON DELETE SET NULL;

ALTER TABLE `field_configuration` ADD CONSTRAINT `fkqrsw1egn2l8w4ve311io2fa5e` FOREIGN KEY (`work_order_request_configuration_id`) REFERENCES `work_order_request_configuration` (`id`) ON DELETE SET NULL;

ALTER TABLE `labor` ADD CONSTRAINT `fk4p8pded3s0v3w690dfbv829im` FOREIGN KEY (`time_category_id`) REFERENCES `time_category` (`id`) ON DELETE SET NULL;

ALTER TABLE `labor` ADD CONSTRAINT `fk88ovjan75lbo5xk7k7omt0xb6` FOREIGN KEY (`work_order_id`) REFERENCES `work_order` (`id`) ON DELETE CASCADE;

ALTER TABLE `labor` ADD CONSTRAINT `fkfqjl9rhhuh45t4tyuo7i5qys9` FOREIGN KEY (`company_id`) REFERENCES `company` (`id`) ON DELETE CASCADE;

ALTER TABLE `labor` ADD CONSTRAINT `fkr5slomxbec1ob9k9npjcdad05` FOREIGN KEY (`assigned_to_id`) REFERENCES `own_user` (`id`) ON DELETE SET NULL;

ALTER TABLE `general_preferences` ADD CONSTRAINT `fk9of1ivg6k1wtws25cck4x7t50` FOREIGN KEY (`company_settings_id`) REFERENCES `company_settings` (`id`) ON DELETE CASCADE;

ALTER TABLE `general_preferences` ADD CONSTRAINT `fkskbryn4bunuqp0op70ogiu4ow` FOREIGN KEY (`currency_id`) REFERENCES `currency` (`id`) ON DELETE SET NULL;

ALTER TABLE `request_portal` ADD CONSTRAINT `fk_request_portal_company` FOREIGN KEY (`company_id`) REFERENCES `company` (`id`);

ALTER TABLE `request_portal_field` ADD CONSTRAINT `fk_request_portal_field_asset` FOREIGN KEY (`asset_id`) REFERENCES `asset` (`id`);

ALTER TABLE `request_portal_field` ADD CONSTRAINT `fk_request_portal_field_company` FOREIGN KEY (`company_id`) REFERENCES `company` (`id`);

ALTER TABLE `request_portal_field` ADD CONSTRAINT `fk_request_portal_field_location` FOREIGN KEY (`location_id`) REFERENCES `location` (`id`);

ALTER TABLE `request_portal_field` ADD CONSTRAINT `fk_request_portal_field_request_portal` FOREIGN KEY (`request_portal_id`) REFERENCES `request_portal` (`id`);

ALTER TABLE `comment` ADD CONSTRAINT `fk_comment_company` FOREIGN KEY (`company_id`) REFERENCES `company` (`id`) ON DELETE CASCADE;

ALTER TABLE `comment` ADD CONSTRAINT `fk_comment_user` FOREIGN KEY (`user_id`) REFERENCES `own_user` (`id`) ON DELETE CASCADE;

ALTER TABLE `comment` ADD CONSTRAINT `fk_comment_work_order` FOREIGN KEY (`work_order_id`) REFERENCES `work_order` (`id`) ON DELETE CASCADE;

ALTER TABLE `webhook_endpoint_asset_statuses` ADD CONSTRAINT `fk_we_asset_status_endpoint` FOREIGN KEY (`webhook_endpoint_id`) REFERENCES `webhook_endpoint` (`id`) ON DELETE CASCADE;

ALTER TABLE `webhook_endpoint_work_order_statuses` ADD CONSTRAINT `fk_we_wo_status_endpoint` FOREIGN KEY (`webhook_endpoint_id`) REFERENCES `webhook_endpoint` (`id`) ON DELETE CASCADE;

ALTER TABLE `webhook_endpoint_work_order_categories` ADD CONSTRAINT `fk_wo_category_endpoint` FOREIGN KEY (`webhook_endpoint_id`) REFERENCES `webhook_endpoint` (`id`) ON DELETE CASCADE;

ALTER TABLE `webhook_endpoint_work_order_categories` ADD CONSTRAINT `fk_wo_category_ref` FOREIGN KEY (`work_order_categories_id`) REFERENCES `work_order_category` (`id`) ON DELETE CASCADE;

ALTER TABLE `webhook_endpoint_wo_fields` ADD CONSTRAINT `fk_we_wo_field_endpoint` FOREIGN KEY (`webhook_endpoint_id`) REFERENCES `webhook_endpoint` (`id`) ON DELETE CASCADE;

ALTER TABLE `webhook_endpoint_part_fields` ADD CONSTRAINT `fk_we_part_field_endpoint` FOREIGN KEY (`webhook_endpoint_id`) REFERENCES `webhook_endpoint` (`id`) ON DELETE CASCADE;

ALTER TABLE `webhook_endpoint` ADD CONSTRAINT `fk_webhook_endpoint_company` FOREIGN KEY (`company_id`) REFERENCES `company` (`id`) ON DELETE CASCADE;

ALTER TABLE `comment_files` ADD CONSTRAINT `fk_comment_file_comment` FOREIGN KEY (`comment_id`) REFERENCES `comment` (`id`) ON DELETE CASCADE;

ALTER TABLE `comment_files` ADD CONSTRAINT `fk_comment_file_file` FOREIGN KEY (`files_id`) REFERENCES `file` (`id`) ON DELETE CASCADE;

ALTER TABLE `custom_field` ADD CONSTRAINT `fk_company_custom_field_settings` FOREIGN KEY (`company_settings_id`) REFERENCES `company_settings` (`id`) ON DELETE CASCADE;

ALTER TABLE `company_custom_field_options` ADD CONSTRAINT `fk_field_options_field` FOREIGN KEY (`field_id`) REFERENCES `custom_field` (`id`) ON DELETE CASCADE;

ALTER TABLE `custom_field_value` ADD CONSTRAINT `fk_custom_field_value_asset` FOREIGN KEY (`asset_id`) REFERENCES `asset` (`id`) ON DELETE CASCADE;

ALTER TABLE `custom_field_value` ADD CONSTRAINT `fk_custom_field_value_customer` FOREIGN KEY (`customer_id`) REFERENCES `customer` (`id`) ON DELETE CASCADE;

ALTER TABLE `custom_field_value` ADD CONSTRAINT `fk_custom_field_value_field` FOREIGN KEY (`custom_field_id`) REFERENCES `custom_field` (`id`) ON DELETE CASCADE;

ALTER TABLE `custom_field_value` ADD CONSTRAINT `fk_custom_field_value_location` FOREIGN KEY (`location_id`) REFERENCES `location` (`id`) ON DELETE CASCADE;

ALTER TABLE `custom_field_value` ADD CONSTRAINT `fk_custom_field_value_meter` FOREIGN KEY (`meter_id`) REFERENCES `meter` (`id`) ON DELETE CASCADE;

ALTER TABLE `custom_field_value` ADD CONSTRAINT `fk_custom_field_value_part` FOREIGN KEY (`part_id`) REFERENCES `part` (`id`) ON DELETE CASCADE;

ALTER TABLE `custom_field_value` ADD CONSTRAINT `fk_custom_field_value_preventive_maintenance` FOREIGN KEY (`preventive_maintenance_id`) REFERENCES `preventive_maintenance` (`id`) ON DELETE CASCADE;

ALTER TABLE `custom_field_value` ADD CONSTRAINT `fk_custom_field_value_request` FOREIGN KEY (`request_id`) REFERENCES `request` (`id`) ON DELETE CASCADE;

ALTER TABLE `custom_field_value` ADD CONSTRAINT `fk_custom_field_value_vendor` FOREIGN KEY (`vendor_id`) REFERENCES `vendor` (`id`) ON DELETE CASCADE;

ALTER TABLE `custom_field_value` ADD CONSTRAINT `fk_custom_field_value_work_order` FOREIGN KEY (`work_order_id`) REFERENCES `work_order` (`id`) ON DELETE CASCADE;

ALTER TABLE `custom_field_value` ADD CONSTRAINT `fk_custom_field_value_work_order_meter_trigger` FOREIGN KEY (`work_order_meter_trigger_id`) REFERENCES `work_order_meter_trigger` (`id`) ON DELETE CASCADE;

ALTER TABLE `shift_configuration_days` ADD CONSTRAINT `fk_shift_config_days_config` FOREIGN KEY (`shift_configuration_id`) REFERENCES `shift_configuration` (`id`) ON DELETE CASCADE;

ALTER TABLE `shift_configuration_exceptions` ADD CONSTRAINT `fk_shift_config_exceptions_config` FOREIGN KEY (`shift_configuration_id`) REFERENCES `shift_configuration` (`id`) ON DELETE CASCADE;

ALTER TABLE `api_key` ADD CONSTRAINT `fk_api_key_company` FOREIGN KEY (`company_id`) REFERENCES `company` (`id`) ON DELETE CASCADE;

ALTER TABLE `api_key` ADD CONSTRAINT `fk_api_key_user` FOREIGN KEY (`user_id`) REFERENCES `own_user` (`id`) ON DELETE CASCADE;

ALTER TABLE `domain_command` ADD CONSTRAINT `domain_command_company_id_fkey` FOREIGN KEY (`company_id`) REFERENCES `company` (`id`) ON DELETE CASCADE;

ALTER TABLE `meter_trigger_state` ADD CONSTRAINT `meter_trigger_state_company_id_fkey` FOREIGN KEY (`company_id`) REFERENCES `company` (`id`) ON DELETE CASCADE;

ALTER TABLE `meter_trigger_state` ADD CONSTRAINT `meter_trigger_state_last_reading_id_fkey` FOREIGN KEY (`last_reading_id`) REFERENCES `reading` (`id`) ON DELETE SET NULL;

ALTER TABLE `meter_trigger_state` ADD CONSTRAINT `meter_trigger_state_trigger_id_fkey` FOREIGN KEY (`trigger_id`) REFERENCES `work_order_meter_trigger` (`id`) ON DELETE CASCADE;

ALTER TABLE `meter_trigger_execution` ADD CONSTRAINT `meter_trigger_execution_company_id_fkey` FOREIGN KEY (`company_id`) REFERENCES `company` (`id`) ON DELETE CASCADE;

ALTER TABLE `meter_trigger_execution` ADD CONSTRAINT `meter_trigger_execution_reading_id_fkey` FOREIGN KEY (`reading_id`) REFERENCES `reading` (`id`) ON DELETE SET NULL;

ALTER TABLE `meter_trigger_execution` ADD CONSTRAINT `meter_trigger_execution_trigger_id_fkey` FOREIGN KEY (`trigger_id`) REFERENCES `work_order_meter_trigger` (`id`) ON DELETE CASCADE;

ALTER TABLE `meter_trigger_execution` ADD CONSTRAINT `meter_trigger_execution_work_order_id_fkey` FOREIGN KEY (`work_order_id`) REFERENCES `work_order` (`id`) ON DELETE SET NULL;

ALTER TABLE `event_retry` ADD CONSTRAINT `event_retry_check` CHECK (((replay_generation >= 0) AND (business_attempt >= 0)));

ALTER TABLE `event_retry` ADD CONSTRAINT `event_retry_company_id_fkey` FOREIGN KEY (`company_id`) REFERENCES `company` (`id`) ON DELETE CASCADE;

ALTER TABLE `event_retry` ADD CONSTRAINT `event_retry_state_check` CHECK (`state` IN ('READY','PROCESSING','RETRY_TAKEN_OVER','DEAD','DONE'));

ALTER TABLE `domain_event_outbox` ADD CONSTRAINT `domain_event_outbox_company_id_fkey` FOREIGN KEY (`company_id`) REFERENCES `company` (`id`) ON DELETE CASCADE;

ALTER TABLE `domain_event_outbox` ADD CONSTRAINT `domain_event_outbox_purpose_check` CHECK (`purpose` IN ('EVENT','DEAD'));

ALTER TABLE `domain_event_outbox` ADD CONSTRAINT `domain_event_outbox_retry_id_fkey` FOREIGN KEY (`retry_id`) REFERENCES `event_retry` (`id`) ON DELETE CASCADE;

ALTER TABLE `domain_event_outbox` ADD CONSTRAINT `domain_event_outbox_status_check` CHECK (`status` IN ('PENDING','PROCESSING','PUBLISHED'));

ALTER TABLE `consumer_receipt` ADD CONSTRAINT `consumer_receipt_company_id_fkey` FOREIGN KEY (`company_id`) REFERENCES `company` (`id`) ON DELETE CASCADE;

ALTER TABLE `event_delivery` ADD CONSTRAINT `event_delivery_company_id_fkey` FOREIGN KEY (`company_id`) REFERENCES `company` (`id`) ON DELETE CASCADE;

ALTER TABLE `event_delivery` ADD CONSTRAINT `event_delivery_state_check` CHECK (`state` IN ('READY','PROCESSING','RETRY','SENT','DEAD','SKIPPED'));

ALTER TABLE `event_replay_audit` ADD CONSTRAINT `event_replay_audit_actor_id_fkey` FOREIGN KEY (`actor_id`) REFERENCES `own_user` (`id`) ON DELETE SET NULL;

ALTER TABLE `event_replay_audit` ADD CONSTRAINT `event_replay_audit_company_id_fkey` FOREIGN KEY (`company_id`) REFERENCES `company` (`id`) ON DELETE CASCADE;

ALTER TABLE `asset_summary_epoch` ADD CONSTRAINT `asset_summary_epoch_asset_id_fkey` FOREIGN KEY (`asset_id`) REFERENCES `asset` (`id`) ON DELETE CASCADE;

ALTER TABLE `asset_summary_epoch` ADD CONSTRAINT `asset_summary_epoch_company_id_fkey` FOREIGN KEY (`company_id`) REFERENCES `company` (`id`) ON DELETE CASCADE;

ALTER TABLE `asset_summary_epoch` ADD CONSTRAINT `asset_summary_epoch_epoch_check` CHECK ((epoch > 0));

ALTER TABLE `auth_session` ADD CONSTRAINT `auth_session_user_id_fkey` FOREIGN KEY (`user_id`) REFERENCES `own_user` (`id`) ON DELETE CASCADE;

ALTER TABLE `refresh_token` ADD CONSTRAINT `fk_refresh_token_user` FOREIGN KEY (`user_id`) REFERENCES `own_user` (`id`) ON DELETE CASCADE;

ALTER TABLE `refresh_token` ADD CONSTRAINT `refresh_token_session_id_fkey` FOREIGN KEY (`session_id`) REFERENCES `auth_session` (`id`) ON DELETE CASCADE;

ALTER TABLE `request_link_audit` ADD CONSTRAINT `request_link_audit_company_id_fkey` FOREIGN KEY (`company_id`) REFERENCES `company` (`id`) ON DELETE CASCADE;

ALTER TABLE `request_link_audit` ADD CONSTRAINT `request_link_audit_reason_check` CHECK (((char_length(TRIM(reason)) >= 5) AND (char_length(TRIM(reason)) <= 500)));

ALTER TABLE `request_link_audit` ADD CONSTRAINT `request_link_audit_request_id_fkey` FOREIGN KEY (`request_id`) REFERENCES `request` (`id`) ON DELETE CASCADE;

ALTER TABLE `event_report_attachment` ADD CONSTRAINT `event_report_attachment_company_id_fkey` FOREIGN KEY (`company_id`) REFERENCES `company` (`id`) ON DELETE CASCADE;

ALTER TABLE `event_report_attachment` ADD CONSTRAINT `event_report_attachment_content_check` CHECK (((octet_length(content) >= 1) AND (octet_length(content) <= 10485760)));

ALTER TABLE `maintenance_schedule_execution` ADD CONSTRAINT `maintenance_schedule_execution_company_id_fkey` FOREIGN KEY (`company_id`) REFERENCES `company` (`id`) ON DELETE CASCADE;

ALTER TABLE `maintenance_schedule_execution` ADD CONSTRAINT `maintenance_schedule_execution_execution_kind_check` CHECK (`execution_kind` IN ('CREATE','REMINDER'));

ALTER TABLE `maintenance_sla_policy` ADD CONSTRAINT `maintenance_sla_policy_check` CHECK (((repair_minutes >= response_minutes) AND (repair_minutes <= 525600)));

ALTER TABLE `maintenance_sla_policy` ADD CONSTRAINT `maintenance_sla_policy_company_id_fkey` FOREIGN KEY (`company_id`) REFERENCES `company` (`id`) ON DELETE CASCADE;

ALTER TABLE `maintenance_sla_policy` ADD CONSTRAINT `maintenance_sla_policy_priority_check` CHECK (((priority >= 0) AND (priority <= 3)));

ALTER TABLE `maintenance_sla_policy` ADD CONSTRAINT `maintenance_sla_policy_response_minutes_check` CHECK (((response_minutes >= 1) AND (response_minutes <= 525600)));

ALTER TABLE `maintenance_sla_case` ADD CONSTRAINT `maintenance_sla_case_check` CHECK (((response_due_at >= started_at) AND (repair_due_at >= response_due_at)));

ALTER TABLE `maintenance_sla_case` ADD CONSTRAINT `maintenance_sla_case_company_id_fkey` FOREIGN KEY (`company_id`) REFERENCES `company` (`id`) ON DELETE CASCADE;

ALTER TABLE `maintenance_sla_case` ADD CONSTRAINT `maintenance_sla_case_priority_check` CHECK (((priority >= 0) AND (priority <= 3)));

ALTER TABLE `maintenance_sla_case` ADD CONSTRAINT `maintenance_sla_case_work_order_id_fkey` FOREIGN KEY (`work_order_id`) REFERENCES `work_order` (`id`) ON DELETE CASCADE;

ALTER TABLE `maintenance_sla_escalation` ADD CONSTRAINT `maintenance_sla_escalation_company_id_fkey` FOREIGN KEY (`company_id`) REFERENCES `company` (`id`) ON DELETE CASCADE;

ALTER TABLE `maintenance_sla_escalation` ADD CONSTRAINT `maintenance_sla_escalation_kind_check` CHECK (`kind` IN ('RESPONSE','REPAIR'));

ALTER TABLE `maintenance_sla_escalation` ADD CONSTRAINT `maintenance_sla_escalation_work_order_id_fkey` FOREIGN KEY (`work_order_id`) REFERENCES `work_order` (`id`) ON DELETE CASCADE;

CREATE TABLE cmms_transaction_lock (lock_key VARCHAR(160) PRIMARY KEY) ENGINE=InnoDB DEFAULT CHARSET=ascii COLLATE=ascii_bin;
