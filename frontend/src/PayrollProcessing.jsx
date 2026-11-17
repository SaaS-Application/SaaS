import { useEffect, useState } from 'react';

function previousMonth() {
  const date = new Date();
  date.setDate(1);
  date.setMonth(date.getMonth() - 1);
  return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}`;
}

function errorText(error) {
  const data = error.response?.data;
  if (typeof data?.message === 'string') return data.message;
  if (data && typeof data === 'object') return Object.values(data).join('. ');
  return 'The request could not be completed. Please try again.';
}

function amount(value) {
  if (value === null || value === undefined) return '—';
  return Number(value).toLocaleString(undefined, { minimumFractionDigits: 2, maximumFractionDigits: 2 });
}

function days(value) {
  if (value === null || value === undefined) return '—';
  return amount(value);
}

export default function PayrollProcessing({ api, role }) {
  const isManager = role === 'COMPANY_ADMIN' || role === 'HR_ADMIN';
  const isSuperAdmin = role === 'SUPER_ADMIN';
  const [period, setPeriod] = useState(previousMonth);
  const [companyId, setCompanyId] = useState('');
  const [history, setHistory] = useState([]);
  const [preview, setPreview] = useState(null);
  const [detail, setDetail] = useState(null);
  const [selectedEmployeeId, setSelectedEmployeeId] = useState(null);
  const [message, setMessage] = useState('');
  const [isLoading, setIsLoading] = useState(false);

  const historyPath = isManager ? '/company-admin/payrolls'
    : isSuperAdmin ? '/super-admin/payrolls' : '/employee/payrolls';

  const loadHistory = async () => {
    setIsLoading(true);
    try {
      const params = isManager
        ? { payPeriod: period }
        : isSuperAdmin
          ? { payPeriod: period, companyId: companyId || undefined }
          : undefined;
      const { data } = await api.get(historyPath, { params });
      setHistory(data);
      setMessage('');
    } catch (error) {
      setMessage(errorText(error));
    } finally {
      setIsLoading(false);
    }
  };

  useEffect(() => {
    loadHistory();
  }, [historyPath, period, companyId]);

  const loadPreview = async (event) => {
    event.preventDefault();
    setIsLoading(true);
    setPreview(null);
    setDetail(null);
    setSelectedEmployeeId(null);
    try {
      const { data } = await api.get(`/company-admin/payroll-periods/${period}/preview`);
      setPreview(data);
      setMessage('');
    } catch (error) {
      setMessage(errorText(error));
    } finally {
      setIsLoading(false);
    }
  };

  const processPayroll = async () => {
    setIsLoading(true);
    try {
      const { data } = await api.post(`/company-admin/payroll-periods/${period}/process`);
      setMessage(`Processed ${data.processedCount} payroll record(s) for ${data.payPeriod}.`);
      setPreview(null);
      setDetail(null);
      await loadHistory();
    } catch (error) {
      setMessage(errorText(error));
    } finally {
      setIsLoading(false);
    }
  };

  const openDetail = async (payrollId) => {
    const path = isManager ? `/company-admin/payrolls/${payrollId}`
      : isSuperAdmin ? `/super-admin/payrolls/${payrollId}`
        : `/employee/payrolls/${payrollId}`;
    setIsLoading(true);
    try {
      const { data } = await api.get(path);
      setDetail(data);
      setMessage('');
    } catch (error) {
      setMessage(errorText(error));
    } finally {
      setIsLoading(false);
    }
  };

  const renderComponents = (components) => (
    components?.length
      ? <div className="table-wrap"><table><thead><tr><th>Component</th><th>Type</th><th>Monthly amount</th><th>Payroll amount</th></tr></thead>
        <tbody>{components.map((component, index) => <tr key={`${component.code}-${index}`}>
          <td><strong>{component.name}</strong><small>{component.code}</small></td>
          <td>{component.type === 'EARNING' ? 'Earning' : 'Deduction'}</td>
          <td>{amount(component.monthlyAmount)}</td><td>{amount(component.payableAmount)}</td>
        </tr>)}</tbody></table></div>
      : <p className="empty-inline">No saved component breakdown is available for this record.</p>
  );

  return <section className="payroll-workspace">
    <div className="page-heading">
      <div>
        <h2>{isManager ? 'Payroll processing' : isSuperAdmin ? 'Platform payroll oversight' : 'My payroll history'}</h2>
        <p>{isManager
          ? 'Preview monthly payroll, resolve validation issues, and process eligible employees.'
          : isSuperAdmin
            ? 'Read-only, platform-wide payroll records.'
            : 'View your processed payroll records and saved salary breakdowns.'}</p>
      </div>
    </div>

    {message && <p className="message" role="alert">{message}</p>}

    {(isManager || isSuperAdmin) && <form className="filter-bar" onSubmit={isManager ? loadPreview : (event) => { event.preventDefault(); loadHistory(); }}>
      <label>Pay period<input required type="month" value={period} onChange={(event) => setPeriod(event.target.value)} /></label>
      {isSuperAdmin && <label>Company ID (optional)<input type="number" min="1" value={companyId} onChange={(event) => setCompanyId(event.target.value)} /></label>}
      {isManager && <button className="secondary-btn" disabled={isLoading || !period}>Preview payroll</button>}
      {isSuperAdmin && <button className="secondary-btn" disabled={isLoading}>Refresh</button>}
    </form>}

    {isManager && preview && <>
      <div className="dashboard-grid">
        <div className="stat-card"><span>Eligible employees</span><strong>{preview.eligibleCount}</strong></div>
        <div className="stat-card"><span>Skipped employees</span><strong>{preview.skippedCount}</strong></div>
        <div className="stat-card"><span>Processing</span><strong>{preview.processingAllowed ? 'Ready' : 'Blocked'}</strong></div>
      </div>
      {preview.warnings?.map((warning) => <p className="helper-text" key={warning}>{warning}</p>)}
      {preview.blockingErrors?.length > 0 && <section className="panel-section validation-panel" role="alert">
        <h3>Resolve before processing</h3>
        <ul>{preview.blockingErrors.map((warning) => <li key={warning}>{warning}</li>)}</ul>
      </section>}
      {preview.employees?.length > 0 && <section className="panel-section">
        <h3>Employee payroll preview · {preview.payPeriod}</h3>
        <div className="table-wrap"><table><thead><tr><th>Employee</th><th>Working days</th><th>Paid days</th><th>Unpaid days</th><th>Gross earnings</th><th>Deductions</th><th>Net salary</th><th>Status</th><th></th></tr></thead>
          <tbody>{preview.employees.map((employee) => <tr key={employee.employeeId}>
            <td><strong>{employee.employeeName}</strong><small>{employee.employeeNumber}</small></td>
            <td>{days(employee.totalWorkingDays)}</td><td>{days(employee.paidDays)}</td><td>{days(employee.unpaidDays)}</td>
            <td>{amount(employee.grossEarnings)}</td><td>{amount(employee.totalDeductions)}</td><td>{amount(employee.netSalary)}</td>
            <td>{employee.alreadyProcessed ? 'Processed' : employee.eligible ? 'Eligible' : 'Needs review'}</td>
            <td>{employee.components?.length > 0 && <button className="table-btn" onClick={() => setSelectedEmployeeId(
              selectedEmployeeId === employee.employeeId ? null : employee.employeeId
            )}>{selectedEmployeeId === employee.employeeId ? 'Hide' : 'Breakdown'}</button>}</td>
          </tr>)}</tbody></table></div>
        {preview.employees.map((employee) => <div key={`${employee.employeeId}-warnings`}>
          {employee.warnings?.length > 0 && <div className="payroll-warning"><strong>{employee.employeeName}</strong><ul>{employee.warnings.map((warning) => <li key={warning}>{warning}</li>)}</ul></div>}
          {selectedEmployeeId === employee.employeeId && renderComponents(employee.components)}
        </div>)}
      </section>}
      <button className="primary-btn" disabled={isLoading || !preview.processingAllowed} onClick={processPayroll}>
        {isLoading ? 'Processing…' : `Process eligible payroll · ${preview.payPeriod}`}
      </button>
    </>}

    <section className="panel-section">
      <div className="page-heading"><h3>{isManager ? `Processed payroll · ${period}` : 'Processed payroll history'}</h3>
        <button className="secondary-btn" disabled={isLoading} onClick={loadHistory}>Refresh history</button></div>
      {isLoading && history.length === 0 ? <p className="loading-state">Loading payroll…</p>
        : history.length === 0 ? <p className="empty-inline">No processed payroll records found.</p>
          : <div className="table-wrap"><table><thead><tr>
            {isSuperAdmin && <th>Company</th>}<th>Employee</th><th>Period</th><th>Paid / working days</th><th>Gross earnings</th><th>Deductions</th><th>Net salary</th><th></th>
          </tr></thead><tbody>{history.map((payroll) => <tr key={payroll.id}>
            {isSuperAdmin && <td>{payroll.companyId}</td>}
            <td><strong>{payroll.employeeName}</strong><small>{payroll.employeeNumber}</small></td>
            <td>{payroll.payPeriod}</td><td>{days(payroll.paidDays)} / {days(payroll.totalWorkingDays)}</td>
            <td>{amount(payroll.grossEarnings)}</td><td>{amount(payroll.totalDeductions)}</td><td>{amount(payroll.netSalary)}</td>
            <td><button className="table-btn" onClick={() => openDetail(payroll.id)}>Details</button></td>
          </tr>)}</tbody></table></div>}
    </section>

    {detail && <section className="panel-section">
      <div className="page-heading"><div><h3>{detail.employeeName} · {detail.payPeriod}</h3><p>{detail.employeeNumber} · {detail.status}</p></div>
        <button className="link-btn" onClick={() => setDetail(null)}>Close</button></div>
      <div className="dashboard-grid">
        <div className="stat-card"><span>Working days</span><strong>{days(detail.totalWorkingDays)}</strong></div>
        <div className="stat-card"><span>Paid days</span><strong>{days(detail.paidDays)}</strong></div>
        <div className="stat-card"><span>Unpaid days</span><strong>{days(detail.unpaidDays)}</strong></div>
        <div className="stat-card"><span>Gross earnings</span><strong>{amount(detail.grossEarnings)}</strong></div>
        <div className="stat-card"><span>Deductions</span><strong>{amount(detail.totalDeductions)}</strong></div>
        <div className="stat-card"><span>Net salary</span><strong>{amount(detail.netSalary)}</strong></div>
      </div>
      {renderComponents(detail.components)}
    </section>}
  </section>;
}
