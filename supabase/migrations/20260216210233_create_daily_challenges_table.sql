
  create table "public"."daily_challenges" (
    "id" uuid not null default gen_random_uuid(),
    "date" date not null,
    "is_guess_country" boolean default false,
    "is_guess_capital" boolean default false,
    "is_guess_flag" boolean default false,
    "answer" text not null,
    "created_at" timestamp with time zone not null default now(),
    "updated_at" timestamp with time zone not null default now()
      );


CREATE UNIQUE INDEX daily_challenges_pkey ON public.daily_challenges USING btree (id);

alter table "public"."daily_challenges" add constraint "daily_challenges_pkey" PRIMARY KEY using index "daily_challenges_pkey";

grant delete on table "public"."daily_challenges" to "anon";

grant insert on table "public"."daily_challenges" to "anon";

grant references on table "public"."daily_challenges" to "anon";

grant select on table "public"."daily_challenges" to "anon";

grant trigger on table "public"."daily_challenges" to "anon";

grant truncate on table "public"."daily_challenges" to "anon";

grant update on table "public"."daily_challenges" to "anon";

grant delete on table "public"."daily_challenges" to "authenticated";

grant insert on table "public"."daily_challenges" to "authenticated";

grant references on table "public"."daily_challenges" to "authenticated";

grant select on table "public"."daily_challenges" to "authenticated";

grant trigger on table "public"."daily_challenges" to "authenticated";

grant truncate on table "public"."daily_challenges" to "authenticated";

grant update on table "public"."daily_challenges" to "authenticated";

grant delete on table "public"."daily_challenges" to "service_role";

grant insert on table "public"."daily_challenges" to "service_role";

grant references on table "public"."daily_challenges" to "service_role";

grant select on table "public"."daily_challenges" to "service_role";

grant trigger on table "public"."daily_challenges" to "service_role";

grant truncate on table "public"."daily_challenges" to "service_role";

grant update on table "public"."daily_challenges" to "service_role";


