/**
 * Copyright (C) 2000 - 2011 Silverpeas
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * As a special exception to the terms and conditions of version 3.0 of
 * the GPL, you may redistribute this Program in connection with Free/Libre
 * Open Source Software ("FLOSS") applications as described in Silverpeas's
 * FLOSS exception.  You should have received a copy of the text describing
 * the FLOSS exception, and it is also available here:
 * "http://repository.silverpeas.com/legal/licensing"
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.stratelia.webactiv.yellowpages.model.dao;

import com.stratelia.webactiv.yellowpages.model.beans.Company;
import com.stratelia.webactiv.yellowpages.model.beans.CompanyPK;

import java.util.List;

public interface CompanyDAO {

    public CompanyPK saveCompany(Company company);

    public void updateCompany(Company company);

    public int deleteCompany(Company company);

    public void addCompanyToContact(Company company, int contactId);

    public void removeCompanyFromContact(Company company, int contactId);

    public List<Company> findCompanyListByContactId(int contactId);

}