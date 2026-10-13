ALTER TABLE wbms.wb_weight_devices
    DROP CONSTRAINT IF EXISTS wb_weight_devices_protocol_type_check;

ALTER TABLE wbms.wb_weight_devices
    ADD CONSTRAINT wb_weight_devices_protocol_type_check
    CHECK (
        protocol_type IS NULL
        OR protocol_type IN (
            'TANROADS_ASCII_AXLE',
            'GENERIC_NUMERIC',
            'METTLER_TOLEDO_CONTINUOUS',
            'METTLER_TOLEDO_SICS',
            'RINSTRUM_R400',
            'AVERY_ZM',
            'PRECIA_MOLEN_I30_I35',
            'YAOHUA_XK3190',
            'SENSOCAR_GENERIC'
        )
    );

ALTER TABLE aud.wb_weight_devices_aud
    DROP CONSTRAINT IF EXISTS wb_weight_devices_aud_protocol_type_check;

ALTER TABLE aud.wb_weight_devices_aud
    ADD CONSTRAINT wb_weight_devices_aud_protocol_type_check
    CHECK (
        protocol_type IS NULL
        OR protocol_type IN (
            'TANROADS_ASCII_AXLE',
            'GENERIC_NUMERIC',
            'METTLER_TOLEDO_CONTINUOUS',
            'METTLER_TOLEDO_SICS',
            'RINSTRUM_R400',
            'AVERY_ZM',
            'PRECIA_MOLEN_I30_I35',
            'YAOHUA_XK3190',
            'SENSOCAR_GENERIC'
        )
    );
