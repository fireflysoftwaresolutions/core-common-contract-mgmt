-- Optional INITIAL_PRIMARY_LOAN_CONTRACT owner protocol. V1-V8 are unchanged; no historical backfill.
-- Preprovision independent NOLOGIN contract_linkage_guard and its two empty private-owned schemas.
BEGIN;
SET LOCAL search_path=pg_catalog;
DO $$ DECLARE owner_oid OID;guard_oid OID;BEGIN
 SELECT oid INTO STRICT guard_oid FROM pg_catalog.pg_roles WHERE rolname='contract_linkage_guard'
  AND NOT rolcanlogin AND NOT rolsuper AND NOT rolbypassrls AND NOT rolinherit AND NOT rolcreatedb AND NOT rolcreaterole AND NOT rolreplication;
 SELECT relowner INTO STRICT owner_oid FROM pg_catalog.pg_class WHERE oid='public.contract'::pg_catalog.regclass AND relkind='r';
 IF owner_oid IS DISTINCT FROM (SELECT oid FROM pg_catalog.pg_roles WHERE rolname=current_user)
  OR (SELECT count(*) FROM pg_catalog.pg_class WHERE oid IN('public.contract'::pg_catalog.regclass,'public.contract_party'::pg_catalog.regclass,'public.contract_line'::pg_catalog.regclass,'public.contract_status_history'::pg_catalog.regclass) AND relowner=owner_oid AND relkind='r')<>4
  OR NOT pg_catalog.pg_has_role(current_user,guard_oid,'SET') OR EXISTS(SELECT 1 FROM pg_catalog.pg_auth_members WHERE member=guard_oid)
  OR NOT EXISTS(SELECT 1 FROM pg_catalog.pg_roles WHERE rolname='firefly_audit_writer' AND rolcanlogin AND NOT rolsuper AND NOT rolbypassrls AND NOT rolinherit AND NOT rolcreatedb AND NOT rolcreaterole AND NOT rolreplication)
  OR pg_catalog.pg_has_role('firefly_audit_writer',guard_oid,'MEMBER') OR pg_catalog.current_setting('server_encoding')<>'UTF8'
  OR (SELECT count(*) FROM pg_catalog.pg_namespace WHERE nspname IN('contract_linkage','contract_linkage_private') AND nspowner=guard_oid)<>2
  OR EXISTS(SELECT 1 FROM pg_catalog.pg_namespace n,LATERAL pg_catalog.aclexplode(coalesce(n.nspacl,pg_catalog.acldefault('n',n.nspowner))) a WHERE n.nspname IN('contract_linkage','contract_linkage_private') AND a.grantee<>guard_oid)
  OR EXISTS(SELECT 1 FROM pg_catalog.pg_class c JOIN pg_catalog.pg_namespace n ON n.oid=c.relnamespace WHERE n.nspname IN('contract_linkage','contract_linkage_private')) THEN
  RAISE EXCEPTION 'Exact independent initial primary loan contract installer required' USING ERRCODE='42501';
 END IF;
 IF NOT EXISTS(SELECT 1 FROM pg_catalog.pg_proc p JOIN pg_catalog.pg_namespace n ON n.oid=p.pronamespace JOIN pg_catalog.pg_roles r ON r.oid=p.proowner
  WHERE n.nspname='public' AND p.proname='firefly_audit_require_completed' AND p.proargtypes='2950 2950 20 25 16'::pg_catalog.oidvector AND p.prosecdef
  AND r.rolname='firefly_audit_guard' AND p.proconfig=ARRAY['search_path=pg_catalog'] AND p.provolatile='v'
  AND pg_catalog.encode(pg_catalog.sha256(pg_catalog.convert_to(p.prosrc,'UTF8')),'hex')='c839ff938bc0695dd61a0d9a80ec537860025f051bc9e02f19572b91e6b0dccf'
  AND pg_catalog.has_function_privilege('contract_linkage_guard',p.oid,'EXECUTE')) THEN
  RAISE EXCEPTION 'Published completed canonical audit deployment required' USING ERRCODE='42501';
 END IF;
END $$;
GRANT SELECT ON public.contract,public.contract_party,public.contract_line,public.contract_status_history TO contract_linkage_guard;
SET LOCAL ROLE contract_linkage_guard;
CREATE TABLE contract_linkage.action_receipt(
 resource_tenant_id UUID NOT NULL CHECK(resource_tenant_id<>'00000000-0000-0000-0000-000000000000'),
 action_id UUID NOT NULL CHECK(action_id<>'00000000-0000-0000-0000-000000000000'),
 purpose TEXT NOT NULL CHECK(purpose='INITIAL_PRIMARY_LOAN_CONTRACT'),
 application_id UUID NOT NULL CHECK(application_id<>'00000000-0000-0000-0000-000000000000'),
 party_id UUID NOT NULL CHECK(party_id<>'00000000-0000-0000-0000-000000000000'),role_in_contract_id UUID NOT NULL CHECK(role_in_contract_id<>'00000000-0000-0000-0000-000000000000'),
 requested_contract_id UUID CHECK(requested_contract_id IS NULL OR requested_contract_id<>'00000000-0000-0000-0000-000000000000'),
 contract_id UUID NOT NULL CHECK(contract_id<>'00000000-0000-0000-0000-000000000000'),contract_party_id UUID NOT NULL CHECK(contract_party_id<>'00000000-0000-0000-0000-000000000000'),
 contract_line_id UUID NOT NULL CHECK(contract_line_id<>'00000000-0000-0000-0000-000000000000'),status_history_id UUID NOT NULL CHECK(status_history_id<>'00000000-0000-0000-0000-000000000000'),
 product_id UUID NOT NULL CHECK(product_id<>'00000000-0000-0000-0000-000000000000'),currency_code VARCHAR(3) NOT NULL CHECK((currency_code COLLATE "C") ~ '^[A-Z]{3}$'),
 contract_created BOOLEAN NOT NULL,contract_party_created BOOLEAN NOT NULL,contract_status_at_link TEXT NOT NULL CHECK(contract_status_at_link IN('DRAFT','ACTIVE')),
 intent_digest TEXT NOT NULL CHECK((intent_digest COLLATE "C") ~ '^[a-f0-9]{64}$'),effect_digest TEXT NOT NULL CHECK((effect_digest COLLATE "C") ~ '^[a-f0-9]{64}$'),
 initial_contract_evidence TEXT NOT NULL CHECK(pg_catalog.octet_length(initial_contract_evidence) BETWEEN 2 AND 16384),
 initial_party_evidence TEXT NOT NULL CHECK(pg_catalog.octet_length(initial_party_evidence) BETWEEN 2 AND 16384),
 initial_line_evidence TEXT NOT NULL CHECK(pg_catalog.octet_length(initial_line_evidence) BETWEEN 2 AND 16384),
 initial_history_evidence TEXT NOT NULL CHECK(pg_catalog.octet_length(initial_history_evidence) BETWEEN 2 AND 16384),
 initial_evidence_digest TEXT NOT NULL CHECK((initial_evidence_digest COLLATE "C") ~ '^[a-f0-9]{64}$'),
 actor_issuer VARCHAR(512) NOT NULL CHECK(pg_catalog.length(actor_issuer)>0),actor_subject VARCHAR(512) NOT NULL CHECK(pg_catalog.length(actor_subject)>0),
 actor_client_id VARCHAR(512) NOT NULL CHECK(pg_catalog.length(actor_client_id)>0),actor_platform_tenant_id VARCHAR(512) NOT NULL CHECK(pg_catalog.length(actor_platform_tenant_id)>0),
 binding_id UUID NOT NULL CHECK(binding_id<>'00000000-0000-0000-0000-000000000000'),binding_version BIGINT NOT NULL CHECK(binding_version>0),binding_digest TEXT NOT NULL CHECK((binding_digest COLLATE "C") ~ '^[a-f0-9]{64}$'),
 policy_revision VARCHAR(128) NOT NULL CHECK((policy_revision COLLATE "C") ~ '^[A-Za-z0-9][A-Za-z0-9._:/-]{0,127}$'),linked_at TIMESTAMPTZ NOT NULL,
 audit_context_nonce UUID NOT NULL CHECK(audit_context_nonce<>'00000000-0000-0000-0000-000000000000'),audit_event_id UUID NOT NULL CHECK(audit_event_id<>'00000000-0000-0000-0000-000000000000'),
 audit_sequence BIGINT NOT NULL CHECK(audit_sequence>0),audit_record_hash TEXT NOT NULL CHECK((audit_record_hash COLLATE "C") ~ '^[a-f0-9]{64}$'),
 PRIMARY KEY(resource_tenant_id,action_id),CONSTRAINT initial_primary_application_unique UNIQUE(resource_tenant_id,application_id,purpose),UNIQUE(resource_tenant_id,audit_event_id),
 CHECK((contract_created AND requested_contract_id IS NULL AND contract_status_at_link='DRAFT' AND contract_party_created)
  OR(NOT contract_created AND requested_contract_id IS NOT NULL AND requested_contract_id=contract_id AND NOT contract_party_created))
);
COMMENT ON CONSTRAINT initial_primary_application_unique ON contract_linkage.action_receipt IS
 'One retained INITIAL_PRIMARY_LOAN_CONTRACT link per application and owner namespace. This is not a limit on all contract purposes.';
CREATE FUNCTION contract_linkage_private.text(value TEXT) RETURNS BYTEA
LANGUAGE plpgsql IMMUTABLE SECURITY INVOKER SET search_path=pg_catalog AS $$
DECLARE encoded BYTEA;BEGIN
 IF value IS NULL OR pg_catalog.octet_length(value)>4096 THEN RAISE EXCEPTION 'Bounded initial contract framing required' USING ERRCODE='23514';END IF;
 encoded:=pg_catalog.convert_to(value,'UTF8');RETURN pg_catalog.int4send(pg_catalog.octet_length(encoded))||encoded;
END $$;
CREATE FUNCTION contract_linkage_private.initial_text(value TEXT) RETURNS BYTEA
LANGUAGE plpgsql IMMUTABLE SECURITY INVOKER SET search_path=pg_catalog AS $$
DECLARE encoded BYTEA;BEGIN
 IF value IS NULL OR pg_catalog.octet_length(value)>16384 THEN RAISE EXCEPTION 'Bounded complete initial contract evidence required' USING ERRCODE='23514';END IF;
 encoded:=pg_catalog.convert_to(value,'UTF8');RETURN pg_catalog.int4send(pg_catalog.octet_length(encoded))||encoded;
END $$;
-- Only initial receipt/contract INSERT invokes this current-transaction predicate. Later owner transitions do not re-run it.
CREATE FUNCTION contract_linkage_private.require_graph(tenant UUID,original_action UUID) RETURNS VOID
LANGUAGE plpgsql VOLATILE SECURITY DEFINER SET search_path=pg_catalog AS $$
DECLARE a contract_linkage.action_receipt%ROWTYPE;c public.contract%ROWTYPE;p public.contract_party%ROWTYPE;l public.contract_line%ROWTYPE;h public.contract_status_history%ROWTYPE;
 intent TEXT;effect TEXT;initial_digest TEXT;raw TEXT;seconds BIGINT;nanos INTEGER;display TIMESTAMP;completed RECORD;
BEGIN
 SELECT * INTO STRICT a FROM contract_linkage.action_receipt WHERE resource_tenant_id=tenant AND action_id=original_action;
 SELECT * INTO STRICT c FROM public.contract WHERE contract_id=a.contract_id;
 SELECT * INTO STRICT p FROM public.contract_party WHERE contract_party_id=a.contract_party_id;
 SELECT * INTO STRICT l FROM public.contract_line WHERE contract_line_id=a.contract_line_id;
 SELECT * INTO STRICT h FROM public.contract_status_history WHERE contract_status_history_id=a.status_history_id;
 FOREACH raw IN ARRAY ARRAY[a.initial_contract_evidence,a.initial_party_evidence,a.initial_line_evidence,a.initial_history_evidence] LOOP
  IF pg_catalog.json_typeof(raw::json)<>'object' OR(SELECT pg_catalog.count(*)<>pg_catalog.count(DISTINCT key) FROM pg_catalog.json_object_keys(raw::json) key) THEN
   RAISE EXCEPTION 'Unique complete initial evidence object fields required' USING ERRCODE='23514';
  END IF;
 END LOOP;
 initial_digest:=pg_catalog.encode(pg_catalog.sha256(pg_catalog.convert_to('firefly.contract.initial-evidence','UTF8')||pg_catalog.decode('01','hex')
  ||contract_linkage_private.initial_text(a.initial_contract_evidence)||contract_linkage_private.initial_text(a.initial_party_evidence)
  ||contract_linkage_private.initial_text(a.initial_line_evidence)||contract_linkage_private.initial_text(a.initial_history_evidence)),'hex');
 IF a.initial_evidence_digest IS DISTINCT FROM initial_digest
  OR a.initial_contract_evidence::jsonb IS DISTINCT FROM pg_catalog.to_jsonb(c)
  OR a.initial_party_evidence::jsonb IS DISTINCT FROM pg_catalog.to_jsonb(p)
  OR a.initial_line_evidence::jsonb IS DISTINCT FROM pg_catalog.to_jsonb(l)
  OR a.initial_history_evidence::jsonb IS DISTINCT FROM pg_catalog.to_jsonb(h) THEN
  RAISE EXCEPTION 'Exact immutable full initial contract row evidence required' USING ERRCODE='23514';
 END IF;
 seconds:=pg_catalog.floor(extract(epoch FROM a.linked_at))::bigint;nanos:=((extract(epoch FROM a.linked_at)-seconds)*1000000000)::integer;display:=a.linked_at AT TIME ZONE 'UTC';
 intent:=pg_catalog.encode(pg_catalog.sha256(pg_catalog.convert_to('firefly.contract.link.intent','UTF8')||pg_catalog.decode('01','hex')||contract_linkage_private.text(a.purpose)
  ||pg_catalog.uuid_send(a.action_id)||pg_catalog.uuid_send(a.application_id)||pg_catalog.uuid_send(a.resource_tenant_id)||pg_catalog.uuid_send(a.party_id)||pg_catalog.uuid_send(a.role_in_contract_id)||pg_catalog.uuid_send(a.product_id)
  ||CASE WHEN a.requested_contract_id IS NULL THEN pg_catalog.decode('00','hex') ELSE pg_catalog.decode('01','hex')||pg_catalog.uuid_send(a.requested_contract_id) END||pg_catalog.convert_to(a.currency_code,'UTF8')),'hex');
 effect:=pg_catalog.encode(pg_catalog.sha256(pg_catalog.convert_to('firefly.contract.link.effect','UTF8')||pg_catalog.decode('02','hex')||pg_catalog.decode(intent,'hex')||pg_catalog.decode(initial_digest,'hex')
  ||pg_catalog.uuid_send(a.contract_id)||pg_catalog.uuid_send(a.contract_party_id)||pg_catalog.uuid_send(a.contract_line_id)||pg_catalog.uuid_send(a.status_history_id)
  ||pg_catalog.decode(CASE WHEN a.contract_created THEN '01' ELSE '00' END,'hex')||pg_catalog.decode(CASE WHEN a.contract_party_created THEN '01' ELSE '00' END,'hex')
  ||contract_linkage_private.text(a.contract_status_at_link)||pg_catalog.int8send(seconds)||pg_catalog.int4send(nanos)
  ||contract_linkage_private.text(a.actor_issuer)||contract_linkage_private.text(a.actor_subject)||contract_linkage_private.text(a.actor_client_id)||contract_linkage_private.text(a.actor_platform_tenant_id)
  ||pg_catalog.uuid_send(a.binding_id)||pg_catalog.int8send(a.binding_version)||pg_catalog.decode(a.binding_digest,'hex')||contract_linkage_private.text(a.policy_revision)),'hex');
 IF a.intent_digest IS DISTINCT FROM intent OR a.effect_digest IS DISTINCT FROM effect
  OR(c.product_id,c.currency_code,c.contract_status::text) IS DISTINCT FROM(a.product_id,a.currency_code,a.contract_status_at_link)
  OR(p.contract_id,p.party_id,p.role_in_contract_id,p.is_active,p.date_left) IS DISTINCT FROM(a.contract_id,a.party_id,a.role_in_contract_id,true,NULL::timestamp)
  OR(l.contract_id,l.product_id,l.is_primary) IS DISTINCT FROM(a.contract_id,a.product_id,true)
  OR(h.contract_id,h.status_code::text,h.status_end_date) IS DISTINCT FROM(a.contract_id,CASE WHEN a.contract_status_at_link='DRAFT' THEN 'CREATED' ELSE 'ACTIVATED' END,NULL::timestamp)
  OR h.status_start_date IS NULL
  OR(SELECT count(*) FROM public.contract_line WHERE contract_id=a.contract_id AND is_primary)<>1
  OR(SELECT count(*) FROM public.contract_status_history WHERE contract_id=a.contract_id AND status_end_date IS NULL)<>1
  OR(SELECT count(*) FROM public.contract_party WHERE contract_id=a.contract_id AND party_id=a.party_id AND role_in_contract_id=a.role_in_contract_id AND is_active)<>1 THEN
  RAISE EXCEPTION 'Exact initial primary loan contract graph required' USING ERRCODE='23514';
 END IF;
 IF a.contract_created AND(
  (c.contract_number,c.start_date,c.end_date,c.product_catalog_id,c.predecessor_contract_id,c.link_kind::text,c.created_at,c.updated_at)
   IS DISTINCT FROM(NULL::varchar,NULL::timestamp,NULL::timestamp,NULL::uuid,NULL::uuid,NULL::text,display,display)
  OR(l.line_role,l.quantity,l.effective_from,l.effective_to,l.created_at,l.updated_at)
   IS DISTINCT FROM(NULL::varchar,NULL::numeric,NULL::timestamp,NULL::timestamp,display,display)
  OR(h.status_start_date,h.created_at,h.updated_at) IS DISTINCT FROM(display,display,display)) THEN
  RAISE EXCEPTION 'No invented initial contract economic or validity data permitted' USING ERRCODE='23514';
 END IF;
 IF a.contract_party_created AND(p.date_joined,p.created_at,p.updated_at) IS DISTINCT FROM(display,display,display) THEN
  RAISE EXCEPTION 'Exact current created contract party required' USING ERRCODE='23514';
 END IF;
 SELECT * INTO STRICT completed FROM public.firefly_audit_require_completed(a.audit_context_nonce,a.audit_event_id,a.audit_sequence,a.audit_record_hash,false);
 IF(completed.resource_tenant_id,completed.service,completed.resource_type,completed.resource_id,completed.event_id,
    completed.actor_issuer,completed.actor_subject,completed.actor_client_id,completed.actor_platform_tenant_id,completed.actor_staff_realm,completed.actor_staff_id,
    completed.action,completed.outcome,completed.process_id,completed.correlation_id,completed.causation_id,completed.occurred_seconds,completed.occurred_nanos,completed.evidence_digest,completed.policy_revision)
  IS DISTINCT FROM(a.resource_tenant_id,'core-contract-management'::text,'contract'::text,a.contract_id,a.audit_event_id,
    a.actor_issuer,a.actor_subject,a.actor_client_id,a.actor_platform_tenant_id,NULL::text,NULL::uuid,
    'contracts.loan.initial-primary.link'::text,'COMPLETED'::text,a.action_id,a.action_id,NULL::uuid,seconds,nanos,a.effect_digest,a.policy_revision) THEN
  RAISE EXCEPTION 'Exact completed initial primary loan contract audit required' USING ERRCODE='23514';
 END IF;
END $$;
CREATE FUNCTION contract_linkage_private.graph_insert() RETURNS TRIGGER
LANGUAGE plpgsql VOLATILE SECURITY DEFINER SET search_path=pg_catalog AS $$
DECLARE r RECORD;matched BOOLEAN:=false;BEGIN
 IF TG_TABLE_SCHEMA='contract_linkage' THEN
  PERFORM contract_linkage_private.require_graph(NEW.resource_tenant_id,NEW.action_id);
 ELSIF session_user='firefly_audit_writer' THEN
  FOR r IN SELECT * FROM contract_linkage.action_receipt WHERE contract_id=NEW.contract_id AND contract_created LOOP
   PERFORM contract_linkage_private.require_graph(r.resource_tenant_id,r.action_id);matched:=true;
  END LOOP;
  IF NOT matched THEN
   RAISE EXCEPTION 'Every initial writer contract requires its exact new graph and completed original action' USING ERRCODE='23514';
  END IF;
 END IF;
 RETURN NULL;
END $$;
CREATE FUNCTION contract_linkage_private.immutable() RETURNS TRIGGER
LANGUAGE plpgsql VOLATILE SECURITY DEFINER SET search_path=pg_catalog AS $$
BEGIN
 RAISE EXCEPTION 'Retained initial primary loan contract receipt cannot be mutated or deleted' USING ERRCODE='23514';
END $$;
CREATE FUNCTION contract_linkage_private.immutable_identity() RETURNS TRIGGER
LANGUAGE plpgsql VOLATILE SECURITY DEFINER SET search_path=pg_catalog AS $$
BEGIN
 IF(pg_catalog.to_jsonb(NEW)->TG_ARGV[0]) IS DISTINCT FROM(pg_catalog.to_jsonb(OLD)->TG_ARGV[0]) THEN
  RAISE EXCEPTION 'Contract graph primary identities cannot be changed' USING ERRCODE='23514';
 END IF;
 RETURN NEW;
END $$;
CREATE CONSTRAINT TRIGGER initial_primary_contract_receipt_graph AFTER INSERT ON contract_linkage.action_receipt DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION contract_linkage_private.graph_insert();
CREATE TRIGGER initial_primary_contract_receipt_immutable BEFORE UPDATE OR DELETE ON contract_linkage.action_receipt FOR EACH ROW EXECUTE FUNCTION contract_linkage_private.immutable();
CREATE TRIGGER initial_primary_contract_receipt_no_truncate BEFORE TRUNCATE ON contract_linkage.action_receipt FOR EACH STATEMENT EXECUTE FUNCTION contract_linkage_private.immutable();
REVOKE ALL ON ALL FUNCTIONS IN SCHEMA contract_linkage_private FROM PUBLIC;
REVOKE ALL ON ALL TABLES IN SCHEMA contract_linkage FROM PUBLIC;
DO $$ BEGIN
 EXECUTE pg_catalog.format('GRANT USAGE ON SCHEMA contract_linkage_private TO %I',session_user);
 EXECUTE pg_catalog.format('GRANT EXECUTE ON FUNCTION contract_linkage_private.graph_insert(),contract_linkage_private.immutable(),contract_linkage_private.immutable_identity() TO %I',session_user);
END $$;
GRANT USAGE ON SCHEMA contract_linkage TO firefly_audit_writer;
GRANT SELECT,INSERT ON contract_linkage.action_receipt TO firefly_audit_writer;
RESET ROLE;
CREATE CONSTRAINT TRIGGER initial_primary_contract_graph AFTER INSERT ON public.contract DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION contract_linkage_private.graph_insert();
CREATE TRIGGER initial_primary_contract_identity BEFORE UPDATE OF contract_id ON public.contract FOR EACH ROW EXECUTE FUNCTION contract_linkage_private.immutable_identity('contract_id');
CREATE TRIGGER initial_primary_party_identity BEFORE UPDATE OF contract_party_id ON public.contract_party FOR EACH ROW EXECUTE FUNCTION contract_linkage_private.immutable_identity('contract_party_id');
CREATE TRIGGER initial_primary_line_identity BEFORE UPDATE OF contract_line_id ON public.contract_line FOR EACH ROW EXECUTE FUNCTION contract_linkage_private.immutable_identity('contract_line_id');
CREATE TRIGGER initial_primary_history_identity BEFORE UPDATE OF contract_status_history_id ON public.contract_status_history FOR EACH ROW EXECUTE FUNCTION contract_linkage_private.immutable_identity('contract_status_history_id');
-- Mutable commercial rows retain the existing owner's lifecycle. This protocol neither grants
-- runtime status/economic updates nor constrains later party/line/history purposes to an old receipt.
GRANT SELECT,INSERT ON public.contract,public.contract_party,public.contract_line,public.contract_status_history TO firefly_audit_writer;
-- PostgreSQL row locks need UPDATE on a column; no economic/party/status column is granted.
GRANT UPDATE(contract_id) ON public.contract TO firefly_audit_writer;
GRANT UPDATE(contract_party_id) ON public.contract_party TO firefly_audit_writer;
GRANT UPDATE(contract_line_id) ON public.contract_line TO firefly_audit_writer;
GRANT UPDATE(contract_status_history_id) ON public.contract_status_history TO firefly_audit_writer;
SET LOCAL ROLE contract_linkage_guard;
DO $$ BEGIN
 EXECUTE pg_catalog.format('REVOKE EXECUTE ON FUNCTION contract_linkage_private.graph_insert(),contract_linkage_private.immutable(),contract_linkage_private.immutable_identity() FROM %I',session_user);
 EXECUTE pg_catalog.format('REVOKE USAGE ON SCHEMA contract_linkage_private FROM %I',session_user);
END $$;
RESET ROLE;
COMMIT;
