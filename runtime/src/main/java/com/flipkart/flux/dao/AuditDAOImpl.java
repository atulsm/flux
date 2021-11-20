/*
 * Copyright 2012-2016, the original author or authors.
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.flipkart.flux.dao;

import com.flipkart.flux.dao.iface.AuditDAO;
import com.flipkart.flux.domain.AuditRecord;
import com.flipkart.flux.persistence.*;
import com.google.inject.name.Named;
import org.hibernate.Criteria;
import org.hibernate.Session;
import org.hibernate.criterion.Restrictions;

import javax.inject.Inject;
import javax.transaction.Transactional;
import java.util.List;

/**
 * <code>AuditDAOImpl</code> is an implementation of {@link AuditDAO} which uses Hibernate to perform operations.
 *
 * @author shyam.akirala
 */
public class AuditDAOImpl extends AbstractDAO<AuditRecord> implements AuditDAO {

    @Inject
    public AuditDAOImpl(@Named("fluxSessionFactoriesContext") SessionFactoryContext sessionFactoryContext) {
        super(sessionFactoryContext);
    }

    @Override
    @Transactional
    @SelectDataSource(type = DataSourceType.READ_WRITE, storage = Storage.SHARDED)
    public List<AuditRecord> findBySMInstanceId(String stateMachineInstanceId) {
        Criteria criteria = currentSession().createCriteria(AuditRecord.class).add(Restrictions.eq("stateMachineInstanceId", stateMachineInstanceId));
        List<AuditRecord> records = criteria.list();
        return records;
    }

    @Override
    @Transactional
    @SelectDataSource(type = DataSourceType.READ_WRITE, storage = Storage.SHARDED)
    public AuditRecord create(String stateMachineId, AuditRecord auditRecord) {
        return super.save(auditRecord);
    }

    @Override
    @Transactional
    @SelectDataSource(type = DataSourceType.READ_WRITE, storage = Storage.SHARDED)
    public AuditRecord findById(String stateMachineId, Long id) {
        return super.findById(AuditRecord.class, id);
    }

    @Override
    public AuditRecord create_NonTransactional(AuditRecord auditRecord, Session session) {
        session.save(auditRecord);
        return auditRecord;
    }
}
