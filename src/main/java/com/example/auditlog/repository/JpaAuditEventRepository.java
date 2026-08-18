package com.example.auditlog.repository;

import com.example.auditlog.domain.AuditEvent;
import com.example.auditlog.domain.AuditEventStatus;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.TypedQuery;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Repository
class JpaAuditEventRepository implements AuditEventRepository {

    @PersistenceContext
    private EntityManager entityManager;

    @Override
    public AuditEvent append(AuditEvent auditEvent) {
        entityManager.persist(auditEvent);
        return auditEvent;
    }

    @Override
    public Optional<AuditEvent> findLatest() {
        return entityManager.createQuery(
                        "select e from AuditEvent e order by e.timestamp desc, e.eventId desc",
                        AuditEvent.class
                )
                .setMaxResults(1)
                .getResultStream()
                .findFirst();
    }

    @Override
    public List<AuditEvent> findAllChronological() {
        return entityManager.createQuery(
                        "select e from AuditEvent e order by e.timestamp asc, e.eventId asc",
                        AuditEvent.class
                )
                .getResultList();
    }

    @Override
    public int archiveOlderThan(Instant cutoff, Instant archivedAt) {
        return entityManager.createQuery("""
                        update AuditEvent e
                        set e.status = :archivedStatus,
                            e.archivedAt = :archivedAt
                        where e.timestamp < :cutoff
                          and e.status = :activeStatus
                        """)
                .setParameter("cutoff", cutoff)
                .setParameter("archivedAt", archivedAt)
                .setParameter("archivedStatus", AuditEventStatus.ARCHIVED)
                .setParameter("activeStatus", AuditEventStatus.ACTIVE)
                .executeUpdate();
    }

    @Override
    public Page<AuditEvent> search(AuditEventSearchCriteria criteria, Pageable pageable) {
        QueryParts parts = buildWhereClause(criteria);
        TypedQuery<AuditEvent> query = entityManager.createQuery(
                "select e from AuditEvent e" + parts.whereClause() + " order by e.timestamp desc, e.eventId desc",
                AuditEvent.class
        );
        TypedQuery<Long> countQuery = entityManager.createQuery(
                "select count(e) from AuditEvent e" + parts.whereClause(),
                Long.class
        );

        parts.bind(query);
        parts.bind(countQuery);

        List<AuditEvent> content = query
                .setFirstResult((int) pageable.getOffset())
                .setMaxResults(pageable.getPageSize())
                .getResultList();
        return new PageImpl<>(content, pageable, countQuery.getSingleResult());
    }

    private QueryParts buildWhereClause(AuditEventSearchCriteria criteria) {
        List<String> predicates = new ArrayList<>();
        List<Parameter> parameters = new ArrayList<>();

        addEquals(predicates, parameters, "actorId", criteria.actorId());
        addEquals(predicates, parameters, "resourceType", criteria.resourceType());
        addEquals(predicates, parameters, "resourceId", criteria.resourceId());
        addEquals(predicates, parameters, "eventType", criteria.eventType());

        if (criteria.from() != null) {
            predicates.add("e.timestamp >= :from");
            parameters.add(new Parameter("from", criteria.from()));
        }
        if (criteria.to() != null) {
            predicates.add("e.timestamp <= :to");
            parameters.add(new Parameter("to", criteria.to()));
        }

        String whereClause = predicates.isEmpty() ? "" : " where " + String.join(" and ", predicates);
        return new QueryParts(whereClause, parameters);
    }

    private void addEquals(List<String> predicates, List<Parameter> parameters, String field, String value) {
        if (value != null && !value.isBlank()) {
            predicates.add("e." + field + " = :" + field);
            parameters.add(new Parameter(field, value));
        }
    }

    private record QueryParts(String whereClause, List<Parameter> parameters) {
        private void bind(jakarta.persistence.Query query) {
            parameters.forEach(parameter -> query.setParameter(parameter.name(), parameter.value()));
        }
    }

    private record Parameter(String name, Object value) {
    }
}
