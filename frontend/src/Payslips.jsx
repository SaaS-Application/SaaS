import { useEffect, useState } from 'react';

function errorText(error) {
  const data = error.response?.data;
  if (data instanceof Blob) {
    return data.text().then((text) => {
      try {
        return JSON.parse(text).message || 'The request could not be completed.';
      } catch {
        return 'The request could not be completed.';
      }
    });
  }
  if (typeof data?.message === 'string') return Promise.resolve(data.message);
  return Promise.resolve('The request could not be completed. Please try again.');
}

function amount(value) {
  if (value === null || value === undefined) return '—';
  return `INR ${Number(value).toLocaleString('en-IN', {
    minimumFractionDigits: 2,
    maximumFractionDigits: 2
  })}`;
}

function downloadName(header, fallback) {
  const match = header?.match(/filename="?([^";]+)"?/i);
  return match?.[1] || fallback;
}

export default function Payslips({ api, role, companies = [] }) {
  const isEmployee = role === 'EMPLOYEE';
  const isManager = role === 'COMPANY_ADMIN' || role === 'HR_ADMIN';
  const isSuperAdmin = role === 'SUPER_ADMIN';
  const [period, setPeriod] = useState('');
  const [search, setSearch] = useState('');
  const [companyId, setCompanyId] = useState('');
  const [appliedSearch, setAppliedSearch] = useState('');
  const [payslips, setPayslips] = useState([]);
  const [detail, setDetail] = useState(null);
  const [message, setMessage] = useState('');
  const [isLoading, setIsLoading] = useState(false);

  const listPath = isEmployee ? '/employee/payslips'
    : isManager ? '/company-admin/payslips'
      : companyId ? `/super-admin/companies/${companyId}/payslips` : null;

  const loadPayslips = async () => {
    if (!listPath) {
      setPayslips([]);
      return;
    }
    setIsLoading(true);
    try {
      const { data } = await api.get(listPath, {
        params: {
          payPeriod: period || undefined,
          search: appliedSearch || undefined
        }
      });
      setPayslips(data);
      setMessage('');
    } catch (error) {
      setMessage(await errorText(error));
    } finally {
      setIsLoading(false);
    }
  };

  useEffect(() => {
    loadPayslips();
  }, [listPath, period, appliedSearch]);

  const downloadPayslip = async (item) => {
    const path = isEmployee
      ? `/employee/payslips/${item.payrollId}/download`
      : isManager
        ? `/company-admin/payslips/${item.payrollId}/download`
        : `/super-admin/companies/${companyId}/payslips/${item.payrollId}/download`;
    setIsLoading(true);
    try {
      const response = await api.get(path, { responseType: 'blob' });
      const url = URL.createObjectURL(response.data);
      const anchor = document.createElement('a');
      anchor.href = url;
      anchor.download = downloadName(response.headers['content-disposition'],
        `payslip-${item.payPeriod}.pdf`);
      document.body.appendChild(anchor);
      anchor.click();
      anchor.remove();
      URL.revokeObjectURL(url);
      setMessage('');
    } catch (error) {
      setMessage(await errorText(error));
    } finally {
      setIsLoading(false);
    }
  };

  const openDetails = async (item) => {
    const path = isEmployee ? `/employee/payrolls/${item.payrollId}`
      : isManager ? `/company-admin/payrolls/${item.payrollId}`
        : `/super-admin/payrolls/${item.payrollId}`;
    setIsLoading(true);
    try {
      const { data } = await api.get(path);
      setDetail(data);
      setMessage('');
    } catch (error) {
      setMessage(await errorText(error));
    } finally {
      setIsLoading(false);
    }
  };

  const applySearch = (event) => {
    event.preventDefault();
    setAppliedSearch(search.trim());
  };

  return <section className="payslip-workspace">
    <div className="page-heading">
      <div>
        <h2>{isEmployee ? 'My payslips' : isSuperAdmin ? 'Company payslip oversight' : 'Company payslips'}</h2>
        <p>{isEmployee
          ? 'Download your processed monthly payslips.'
          : isSuperAdmin
            ? 'Read-only payslip access for the selected company.'
            : 'Search employees, review processed payroll details, and download payslips.'}</p>
      </div>
    </div>

    {message && <p className="message" role="alert">{message}</p>}

    <form className="filter-bar" onSubmit={applySearch}>
      {isSuperAdmin && <label>Company
        <select required value={companyId} onChange={(event) => setCompanyId(event.target.value)}>
          <option value="">Choose company</option>
          {companies.map((company) => <option key={company.id} value={company.id}>{company.name}</option>)}
        </select>
      </label>}
      <label>Pay period<input type="month" value={period} onChange={(event) => setPeriod(event.target.value)} /></label>
      {!isEmployee && <label className="search-field">Employee
        <input placeholder="Name or employee number" value={search}
          onChange={(event) => setSearch(event.target.value)} />
      </label>}
      {!isEmployee && <button className="secondary-btn" disabled={isLoading || (isSuperAdmin && !companyId)}>
        Apply filters
      </button>}
      {isEmployee && <button className="secondary-btn" type="button" disabled={isLoading} onClick={loadPayslips}>
        Refresh
      </button>}
    </form>

    <section className="panel-section">
      <div className="page-heading"><h3>{period ? `Payslips · ${period}` : 'Processed payslip history'}</h3>
        {!isEmployee && <button className="secondary-btn" disabled={isLoading || (isSuperAdmin && !companyId)}
          onClick={loadPayslips}>Refresh</button>}
      </div>
      {isSuperAdmin && !companyId
        ? <p className="empty-inline">Choose a company to view its payslips.</p>
        : isLoading && payslips.length === 0
          ? <p className="loading-state">Loading payslips…</p>
          : payslips.length === 0
            ? <div className="empty-state"><h3>No payslips available</h3>
              <p>No processed payslips match the selected period and search.</p></div>
            : <div className="table-wrap"><table><thead><tr>
              {!isEmployee && <th>Employee</th>}<th>Period</th><th>Gross earnings</th><th>Deductions</th>
              <th>Net salary</th><th>Profile</th><th>Actions</th>
            </tr></thead><tbody>{payslips.map((item) => <tr key={item.payrollId}>
              {!isEmployee && <td><strong>{item.employeeName}</strong><small>{item.employeeNumber}</small></td>}
              <td>{item.payPeriod}</td><td>{amount(item.grossEarnings)}</td>
              <td>{amount(item.totalDeductions)}</td><td>{amount(item.netSalary)}</td>
              <td>{item.profileSnapshotAvailable ? 'Payroll snapshot' : 'Current profile'}</td>
              <td className="row-actions">
                <button className="table-btn" onClick={() => openDetails(item)}>Details</button>
                <button className="table-btn" disabled={!item.downloadable || isLoading}
                  onClick={() => downloadPayslip(item)}>
                  {item.downloadable ? 'Download PDF' : 'Snapshot unavailable'}
                </button>
              </td>
            </tr>)}</tbody></table></div>}
    </section>

    {detail && <section className="panel-section">
      <div className="page-heading"><div><h3>{detail.employeeName} · {detail.payPeriod}</h3>
        <p>{detail.employeeNumber} · {detail.status}</p></div>
        <button className="link-btn" onClick={() => setDetail(null)}>Close</button></div>
      <div className="dashboard-grid">
        <div className="stat-card"><span>Working days</span><strong>{detail.totalWorkingDays ?? '—'}</strong></div>
        <div className="stat-card"><span>Paid days</span><strong>{detail.paidDays ?? '—'}</strong></div>
        <div className="stat-card"><span>Unpaid / LOP days</span><strong>{detail.unpaidDays ?? '—'}</strong></div>
        <div className="stat-card"><span>Gross earnings</span><strong>{amount(detail.grossEarnings)}</strong></div>
        <div className="stat-card"><span>Deductions</span><strong>{amount(detail.totalDeductions)}</strong></div>
        <div className="stat-card"><span>Net salary</span><strong>{amount(detail.netSalary)}</strong></div>
      </div>
      {detail.components?.length > 0
        ? <div className="table-wrap"><table><thead><tr><th>Component</th><th>Type</th><th>Amount</th></tr></thead>
          <tbody>{detail.components.map((component, index) => <tr key={`${component.code}-${index}`}>
            <td><strong>{component.name}</strong><small>{component.code}</small></td>
            <td>{component.type === 'EARNING' ? 'Earning' : 'Deduction'}</td>
            <td>{amount(component.payableAmount)}</td>
          </tr>)}</tbody></table></div>
        : <p className="empty-inline">This historical payroll has no saved component breakdown.</p>}
    </section>}
  </section>;
}
