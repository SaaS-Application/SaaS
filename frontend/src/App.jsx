import { useEffect, useMemo, useState } from 'react';
import axios from 'axios';
import PayrollProcessing from './PayrollProcessing';
import Payslips from './Payslips';

const api = axios.create({ baseURL: 'http://localhost:8080/api' });
api.interceptors.request.use((config) => {
  const token = localStorage.getItem('payrollToken');
  if (token) config.headers.Authorization = `Bearer ${token}`;
  return config;
});

const emptyEmployee = {
  employeeNumber: '', firstName: '', lastName: '', email: '', phone: '', address: '',
  dateOfBirth: '', gender: '', dateOfJoining: '', employmentType: 'FULL_TIME',
  jobTitle: '', reportingManagerId: '', emergencyContactName: '', emergencyContactPhone: '',
  emergencyContactRelationship: '', departmentId: '', designationId: '', status: 'ACTIVE'
};
const today = new Date().toISOString().slice(0, 10);
const currentMonth = today.slice(0, 7);
const emptyAttendance = { employeeId: '', date: today, status: 'PRESENT', checkInTime: '', checkOutTime: '', notes: '' };
const defaultAttendanceRules = {
  workStartTime: '09:00:00', workEndTime: '17:00:00', requiredWorkingMinutes: 480,
  gracePeriodMinutes: 15, halfDayThresholdMinutes: 240, overtimeThresholdMinutes: 30,
  weeklyHolidays: ['SATURDAY', 'SUNDAY'], timeZoneId: 'UTC'
};

function storedUser() {
  try {
    return JSON.parse(localStorage.getItem('payrollUser') || 'null');
  } catch {
    return null;
  }
}

function errorText(error) {
  const data = error.response?.data;
  if (typeof data?.message === 'string') return data.message;
  if (data && typeof data === 'object') return Object.values(data).join('. ');
  return 'The request could not be completed. Please try again.';
}

function App() {
  const [token, setToken] = useState(localStorage.getItem('payrollToken') || '');
  const [user, setUser] = useState(storedUser);
  const [view, setView] = useState(() => {
    if (!localStorage.getItem('payrollToken')) return 'login';
    const role = storedUser()?.role;
    return role === 'SUPER_ADMIN' ? 'super-admin' : role === 'EMPLOYEE' ? 'my-attendance' : 'employees';
  });
  const [message, setMessage] = useState('');
  const [isLoading, setIsLoading] = useState(false);
  const [loginForm, setLoginForm] = useState({ email: '', password: '' });
  const [forgotEmail, setForgotEmail] = useState('');
  const [resetForm, setResetForm] = useState({ token: '', newPassword: '' });
  const [stats, setStats] = useState(null);
  const [companies, setCompanies] = useState([]);
  const [companyForm, setCompanyForm] = useState({
    companyName: '', companyCode: '', companyEmail: '', companyPhone: '',
    companyAddress: '', subscriptionPlan: 'PRO', adminUsername: '', adminPassword: ''
  });

  const [employees, setEmployees] = useState({ content: [], number: 0, totalPages: 0, totalElements: 0 });
  const [managerEmployees, setManagerEmployees] = useState([]);
  const [departments, setDepartments] = useState([]);
  const [designations, setDesignations] = useState([]);
  const [filters, setFilters] = useState({ search: '', departmentId: '', designationId: '', employmentType: '', status: '' });
  const [employeeForm, setEmployeeForm] = useState(emptyEmployee);
  const [editingEmployeeId, setEditingEmployeeId] = useState(null);
  const [selectedEmployee, setSelectedEmployee] = useState(null);
  const [orgKind, setOrgKind] = useState('departments');
  const [orgForm, setOrgForm] = useState({ name: '', id: null });
  const [platformCompany, setPlatformCompany] = useState(null);
  const [platformEmployees, setPlatformEmployees] = useState([]);
  const [attendancePage, setAttendancePage] = useState({ content: [], number: 0, totalPages: 0, totalElements: 0 });
  const [attendanceFilters, setAttendanceFilters] = useState({ date: today, search: '', departmentId: '', status: '' });
  const [attendanceMonth, setAttendanceMonth] = useState(currentMonth);
  const [attendanceSummary, setAttendanceSummary] = useState(null);
  const [attendanceReport, setAttendanceReport] = useState([]);
  const [attendanceEmployees, setAttendanceEmployees] = useState([]);
  const [attendanceForm, setAttendanceForm] = useState(emptyAttendance);
  const [editingAttendanceId, setEditingAttendanceId] = useState(null);
  const [attendanceRules, setAttendanceRules] = useState(defaultAttendanceRules);
  const [attendanceHistory, setAttendanceHistory] = useState([]);
  const [todayAttendance, setTodayAttendance] = useState(null);
  const [historyRange, setHistoryRange] = useState({ from: `${currentMonth}-01`, to: today });
  const [platformAttendance, setPlatformAttendance] = useState([]);
  const [platformAttendanceCompanyId, setPlatformAttendanceCompanyId] = useState('');
  const [platformAttendanceDate, setPlatformAttendanceDate] = useState(today);
  const [leaveTypes, setLeaveTypes] = useState([]);
  const [leaveApplications, setLeaveApplications] = useState({ content: [], number: 0, totalPages: 0, totalElements: 0 });
  const [leaveBalances, setLeaveBalances] = useState([]);
  const [leaveOverview, setLeaveOverview] = useState(null);
  const [leaveHolidays, setLeaveHolidays] = useState([]);
  const [leaveEmployees, setLeaveEmployees] = useState([]);
  const [leaveFilters, setLeaveFilters] = useState({ status: '', search: '', from: '', to: '', employeeId: '' });
  const [leaveYear, setLeaveYear] = useState(String(new Date().getFullYear()));
  const [leaveForm, setLeaveForm] = useState({
    leaveTypeId: '', startDate: '', endDate: '', halfDay: false, halfDayPeriod: '', reason: '', document: null
  });
  const [leaveTypeForm, setLeaveTypeForm] = useState({
    code: '', name: '', paid: true, annualEntitlement: '10', carryForwardLimit: '0',
    maxConsecutiveDays: '30', supportingDocumentRequired: false, allowPastApplications: false,
    allowHalfDay: true, allowNegativeBalance: false
  });
  const [leaveAllocationForm, setLeaveAllocationForm] = useState({
    employeeId: '', leaveTypeId: '', leaveYear: String(new Date().getFullYear()),
    openingBalance: '0', annualEntitlement: '', carryForward: '0', accrued: '0', comment: ''
  });
  const [holidayForm, setHolidayForm] = useState({ name: '', date: '', description: '' });
  const [platformLeaveOverview, setPlatformLeaveOverview] = useState(null);
  const [salaryComponents, setSalaryComponents] = useState([]);
  const [salaryStructures, setSalaryStructures] = useState([]);
  const [salaryEmployees, setSalaryEmployees] = useState([]);
  const [salaryAssignments, setSalaryAssignments] = useState([]);
  const [salaryEmployeeId, setSalaryEmployeeId] = useState('');
  const [salaryHistory, setSalaryHistory] = useState([]);
  const [employeeSalaryHistory, setEmployeeSalaryHistory] = useState([]);
  const [platformSalarySummary, setPlatformSalarySummary] = useState(null);
  const [editingSalaryComponentId, setEditingSalaryComponentId] = useState(null);
  const [editingSalaryStructureId, setEditingSalaryStructureId] = useState(null);
  const [salaryComponentForm, setSalaryComponentForm] = useState({
    code: '', name: '', type: 'EARNING', calculationMethod: 'FIXED_AMOUNT',
    taxable: true, effectiveFrom: today, effectiveTo: ''
  });
  const [salaryStructureForm, setSalaryStructureForm] = useState({
    code: '', name: '', effectiveFrom: today, effectiveTo: '',
    components: []
  });
  const [salaryLineForm, setSalaryLineForm] = useState({
    componentId: '', value: '', percentageBaseComponentId: ''
  });
  const [salaryAssignmentForm, setSalaryAssignmentForm] = useState({
    employeeId: '', salaryStructureId: '', effectiveFrom: today, effectiveTo: ''
  });

  const canManage = user?.role === 'COMPANY_ADMIN' || user?.role === 'HR_ADMIN';
  const employeeManagementUrl = useMemo(() => `/company-admin/${orgKind}`, [orgKind]);

  const clearMessage = () => setMessage('');

  const loadEmployees = async (page = 0) => {
    setIsLoading(true);
    clearMessage();
    try {
      const { data } = await api.get('/company-admin/employees', {
        params: {
          ...filters,
          search: filters.search.trim() || undefined,
          departmentId: filters.departmentId || undefined,
          designationId: filters.designationId || undefined,
          employmentType: filters.employmentType || undefined,
          status: filters.status || undefined,
          page,
          size: 10
        }
      });
      setEmployees(data);
    } catch (error) {
      setMessage(errorText(error));
    } finally {
      setIsLoading(false);
    }
  };

  const loadOrganization = async () => {
    try {
      const [departmentResponse, designationResponse] = await Promise.all([
        api.get('/company-admin/departments'),
        api.get('/company-admin/designations')
      ]);
      setDepartments(departmentResponse.data);
      setDesignations(designationResponse.data);
    } catch (error) {
      setMessage(errorText(error));
    }
  };

  const loadCompanyAttendance = async (page = 0) => {
    setIsLoading(true);
    const [year, month] = attendanceMonth.split('-').map(Number);
    const monthEnd = new Date(Date.UTC(year, month, 0)).toISOString().slice(0, 10);
    try {
      const [dailyResponse, summaryResponse, reportResponse] = await Promise.all([
        api.get('/company-admin/attendance/daily', {
          params: {
            date: attendanceFilters.date,
            search: attendanceFilters.search.trim() || undefined,
            departmentId: attendanceFilters.departmentId || undefined,
            status: attendanceFilters.status || undefined,
            page,
            size: 10
          }
        }),
        api.get('/company-admin/attendance/monthly', { params: { month: attendanceMonth } }),
        api.get('/company-admin/attendance/reports', {
          params: { from: `${attendanceMonth}-01`, to: monthEnd, departmentId: attendanceFilters.departmentId || undefined }
        })
      ]);
      setAttendancePage(dailyResponse.data);
      setAttendanceSummary(summaryResponse.data);
      setAttendanceReport(reportResponse.data);
    } catch (error) {
      setMessage(errorText(error));
    } finally {
      setIsLoading(false);
    }
  };

  const loadAttendanceEmployees = async () => {
    try {
      const [employeeResponse, departmentResponse] = await Promise.all([
        api.get('/company-admin/employees', { params: { status: 'ACTIVE', page: 0, size: 100 } }),
        api.get('/company-admin/departments')
      ]);
      setAttendanceEmployees(employeeResponse.data.content);
      setDepartments(departmentResponse.data);
    } catch (error) {
      setMessage(errorText(error));
    }
  };

  const loadAttendanceRules = async () => {
    setIsLoading(true);
    clearMessage();
    try {
      const { data } = await api.get('/company-admin/attendance/rules');
      setAttendanceRules({
        ...data,
        workStartTime: data.workStartTime?.slice(0, 5) || '09:00',
        workEndTime: data.workEndTime?.slice(0, 5) || '17:00'
      });
    } catch (error) {
      setMessage(errorText(error));
    } finally {
      setIsLoading(false);
    }
  };

  const loadMyAttendance = async () => {
    setIsLoading(true);
    try {
      const [historyResponse, summaryResponse, todayResponse] = await Promise.all([
        api.get('/employee/attendance/history', { params: historyRange }),
        api.get('/employee/attendance/monthly', { params: { month: attendanceMonth } }),
        api.get('/employee/attendance/history', { params: { from: today, to: today } })
      ]);
      setAttendanceHistory(historyResponse.data);
      setAttendanceSummary(summaryResponse.data);
      setTodayAttendance(todayResponse.data[0] || null);
    } catch (error) {
      setMessage(errorText(error));
    } finally {
      setIsLoading(false);
    }
  };

  const loadEmployeeLeave = async () => {
    setIsLoading(true);
    clearMessage();
    try {
      const [typeResponse, balanceResponse, applicationResponse] = await Promise.all([
        api.get('/employee/leave/types'),
        api.get('/employee/leave/balances', { params: { year: leaveYear } }),
        api.get('/employee/leave/applications', { params: { page: 0, size: 50 } })
      ]);
      setLeaveTypes(typeResponse.data);
      setLeaveBalances(balanceResponse.data);
      setLeaveApplications(applicationResponse.data);
      setLeaveForm((current) => ({
        ...current,
        leaveTypeId: current.leaveTypeId || String(typeResponse.data[0]?.id || '')
      }));
    } catch (error) {
      setMessage(errorText(error));
    } finally {
      setIsLoading(false);
    }
  };

  const loadCompanyLeave = async (page = 0) => {
    setIsLoading(true);
    clearMessage();
    try {
      const [overviewResponse, typeResponse, holidayResponse, applicationResponse, employeeResponse] = await Promise.all([
        api.get('/company-admin/leave/overview'),
        api.get('/company-admin/leave/types'),
        api.get('/company-admin/leave/holidays'),
        api.get('/company-admin/leave/applications', {
          params: {
            status: leaveFilters.status || undefined,
            search: leaveFilters.search.trim() || undefined,
            from: leaveFilters.from || undefined,
            to: leaveFilters.to || undefined,
            employeeId: leaveFilters.employeeId || undefined,
            page,
            size: 20
          }
        }),
        api.get('/company-admin/employees', { params: { page: 0, size: 100 } })
      ]);
      setLeaveOverview(overviewResponse.data);
      setLeaveTypes(typeResponse.data);
      setLeaveHolidays(holidayResponse.data);
      setLeaveApplications(applicationResponse.data);
      setLeaveEmployees(employeeResponse.data.content);
      setLeaveAllocationForm((current) => ({
        ...current,
        employeeId: current.employeeId || String(employeeResponse.data.content[0]?.id || ''),
        leaveTypeId: current.leaveTypeId || String(typeResponse.data[0]?.id || '')
      }));
    } catch (error) {
      setMessage(errorText(error));
    } finally {
      setIsLoading(false);
    }
  };

  const submitLeaveApplication = async (event) => {
    event.preventDefault();
    setIsLoading(true);
    clearMessage();
    const request = {
      leaveTypeId: Number(leaveForm.leaveTypeId),
      startDate: leaveForm.startDate,
      endDate: leaveForm.halfDay ? leaveForm.startDate : leaveForm.endDate,
      halfDay: leaveForm.halfDay,
      halfDayPeriod: leaveForm.halfDay ? leaveForm.halfDayPeriod : null,
      reason: leaveForm.reason
    };
    try {
      if (leaveForm.document) {
        const body = new FormData();
        body.append('request', new Blob([JSON.stringify(request)], { type: 'application/json' }));
        body.append('document', leaveForm.document);
        await api.post('/employee/leave/applications/with-document', body);
      } else {
        await api.post('/employee/leave/applications', request);
      }
      setLeaveForm({ leaveTypeId: leaveForm.leaveTypeId, startDate: '', endDate: '', halfDay: false, halfDayPeriod: '', reason: '', document: null });
      await loadEmployeeLeave();
      setMessage('Leave application submitted.');
    } catch (error) {
      setMessage(errorText(error));
    } finally {
      setIsLoading(false);
    }
  };

  const submitLeaveType = async (event) => {
    event.preventDefault();
    setIsLoading(true);
    clearMessage();
    try {
      await api.post('/company-admin/leave/types', {
        ...leaveTypeForm,
        annualEntitlement: Number(leaveTypeForm.annualEntitlement),
        carryForwardLimit: Number(leaveTypeForm.carryForwardLimit),
        maxConsecutiveDays: Number(leaveTypeForm.maxConsecutiveDays)
      });
      setLeaveTypeForm({
        code: '', name: '', paid: true, annualEntitlement: '10', carryForwardLimit: '0',
        maxConsecutiveDays: '30', supportingDocumentRequired: false, allowPastApplications: false,
        allowHalfDay: true, allowNegativeBalance: false
      });
      await loadCompanyLeave(leaveApplications.number);
      setMessage('Leave type created.');
    } catch (error) {
      setMessage(errorText(error));
    } finally {
      setIsLoading(false);
    }
  };

  const submitLeaveAllocation = async (event) => {
    event.preventDefault();
    if (!window.confirm('Save this employee leave-balance allocation?')) return;
    setIsLoading(true);
    clearMessage();
    try {
      await api.post('/company-admin/leave/balances', {
        ...leaveAllocationForm,
        employeeId: Number(leaveAllocationForm.employeeId),
        leaveTypeId: Number(leaveAllocationForm.leaveTypeId),
        leaveYear: Number(leaveAllocationForm.leaveYear),
        openingBalance: Number(leaveAllocationForm.openingBalance),
        annualEntitlement: leaveAllocationForm.annualEntitlement === '' ? null : Number(leaveAllocationForm.annualEntitlement),
        carryForward: Number(leaveAllocationForm.carryForward),
        accrued: Number(leaveAllocationForm.accrued)
      });
      await loadCompanyLeave(leaveApplications.number);
      setMessage('Leave balance allocated.');
    } catch (error) {
      setMessage(errorText(error));
    } finally {
      setIsLoading(false);
    }
  };

  const submitCompanyHoliday = async (event) => {
    event.preventDefault();
    setIsLoading(true);
    clearMessage();
    try {
      await api.post('/company-admin/leave/holidays', holidayForm);
      setHolidayForm({ name: '', date: '', description: '' });
      await loadCompanyLeave(leaveApplications.number);
      setMessage('Company holiday added.');
    } catch (error) {
      setMessage(errorText(error));
    } finally {
      setIsLoading(false);
    }
  };

  const decideLeaveApplication = async (application, approve) => {
    const isCancellation = application.status === 'CANCELLATION_PENDING';
    const action = approve ? (isCancellation ? 'Approve cancellation' : 'Approve leave') : (isCancellation ? 'Reject cancellation' : 'Reject leave');
    if (!window.confirm(`${action} for ${application.employeeName}?`)) return;
    const comment = approve ? '' : window.prompt('Enter a comment for this decision:');
    if (!approve && (comment === null || !comment.trim())) return;
    setIsLoading(true);
    clearMessage();
    try {
      if (isCancellation) {
        await api.patch(`/company-admin/leave/applications/${application.id}/cancellation`, { approve, comment });
      } else {
        await api.patch(`/company-admin/leave/applications/${application.id}/decision`, {
          decision: approve ? 'APPROVE' : 'REJECT',
          comment
        });
      }
      await loadCompanyLeave(leaveApplications.number);
      setMessage(approve ? 'Leave decision saved.' : 'Leave request declined.');
    } catch (error) {
      setMessage(errorText(error));
    } finally {
      setIsLoading(false);
    }
  };

  const changeLeaveRequest = async (application, action) => {
    const cancelRequest = action === 'cancellation';
    const prompt = cancelRequest ? 'Request cancellation of this approved leave?' : 'Withdraw this pending leave application?';
    if (!window.confirm(prompt)) return;
    const comment = cancelRequest ? window.prompt('Reason for cancellation:') : null;
    if (cancelRequest && (comment === null || !comment.trim())) return;
    setIsLoading(true);
    clearMessage();
    try {
      if (cancelRequest) {
        await api.patch(`/employee/leave/applications/${application.id}/cancellation`, { comment });
      } else {
        await api.patch(`/employee/leave/applications/${application.id}/withdraw`);
      }
      await loadEmployeeLeave();
      setMessage(cancelRequest ? 'Cancellation request sent.' : 'Leave application withdrawn.');
    } catch (error) {
      setMessage(errorText(error));
    } finally {
      setIsLoading(false);
    }
  };

  const toggleLeaveType = async (type) => {
    if (!window.confirm(`${type.active ? 'Deactivate' : 'Activate'} the ${type.name} leave type?`)) return;
    setIsLoading(true);
    clearMessage();
    try {
      await api.patch(`/company-admin/leave/types/${type.id}/status`, { active: !type.active });
      await loadCompanyLeave(leaveApplications.number);
    } catch (error) {
      setMessage(errorText(error));
    } finally {
      setIsLoading(false);
    }
  };

  const toggleCompanyHoliday = async (holiday) => {
    if (!window.confirm(`${holiday.active ? 'Deactivate' : 'Activate'} ${holiday.name}?`)) return;
    setIsLoading(true);
    clearMessage();
    try {
      await api.patch(`/company-admin/leave/holidays/${holiday.id}/status`, { active: !holiday.active });
      await loadCompanyLeave(leaveApplications.number);
    } catch (error) {
      setMessage(errorText(error));
    } finally {
      setIsLoading(false);
    }
  };

  const downloadLeaveDocument = async (application, employeeSelfService) => {
    setIsLoading(true);
    clearMessage();
    try {
      const base = employeeSelfService ? '/employee/leave' : '/company-admin/leave';
      const { data } = await api.get(`${base}/applications/${application.id}/document`, { responseType: 'blob' });
      const url = URL.createObjectURL(data);
      const link = document.createElement('a');
      link.href = url;
      link.download = `leave-support-${application.id}`;
      link.click();
      URL.revokeObjectURL(url);
    } catch (error) {
      setMessage(errorText(error));
    } finally {
      setIsLoading(false);
    }
  };

  const viewPlatformLeave = async (company) => {
    setIsLoading(true);
    clearMessage();
    try {
      const { data } = await api.get(`/super-admin/companies/${company.id}/leave/overview`);
      setPlatformLeaveOverview(data);
    } catch (error) {
      setMessage(errorText(error));
    } finally {
      setIsLoading(false);
    }
  };

  const loadCompanySalary = async () => {
    setIsLoading(true);
    clearMessage();
    try {
      const [componentResponse, structureResponse, employeeResponse] = await Promise.all([
        api.get('/company-admin/salary/components'),
        api.get('/company-admin/salary/structures'),
        api.get('/company-admin/employees', { params: { page: 0, size: 100 } })
      ]);
      setSalaryComponents(componentResponse.data);
      setSalaryStructures(structureResponse.data);
      setSalaryEmployees(employeeResponse.data.content);
      setSalaryAssignmentForm((current) => ({
        ...current,
        employeeId: current.employeeId || String(employeeResponse.data.content[0]?.id || ''),
        salaryStructureId: current.salaryStructureId || String(structureResponse.data.find((item) => item.active)?.id || '')
      }));
    } catch (error) {
      setMessage(errorText(error));
    } finally {
      setIsLoading(false);
    }
  };

  const loadSalaryHistory = async (employeeId) => {
    if (!employeeId) {
      setSalaryHistory([]);
      return;
    }
    try {
      const { data } = await api.get(`/company-admin/salary/employees/${employeeId}/assignments`);
      setSalaryHistory(data);
    } catch (error) {
      setMessage(errorText(error));
    }
  };

  const loadEmployeeSalary = async () => {
    setIsLoading(true);
    clearMessage();
    try {
      const { data } = await api.get('/employee/salary/history');
      setEmployeeSalaryHistory(data);
    } catch (error) {
      setMessage(errorText(error));
    } finally {
      setIsLoading(false);
    }
  };

  const loadPlatformSalary = async () => {
    setIsLoading(true);
    clearMessage();
    try {
      const { data } = await api.get('/super-admin/salary/summary');
      setPlatformSalarySummary(data);
    } catch (error) {
      setMessage(errorText(error));
    } finally {
      setIsLoading(false);
    }
  };

  const submitSalaryComponent = async (event) => {
    event.preventDefault();
    setIsLoading(true);
    clearMessage();
    try {
      const payload = { ...salaryComponentForm, effectiveTo: salaryComponentForm.effectiveTo || null };
      if (editingSalaryComponentId) {
        await api.put(`/company-admin/salary/components/${editingSalaryComponentId}`, payload);
      } else {
        await api.post('/company-admin/salary/components', payload);
      }
      setSalaryComponentForm({
        code: '', name: '', type: 'EARNING', calculationMethod: 'FIXED_AMOUNT',
        taxable: true, effectiveFrom: today, effectiveTo: ''
      });
      setEditingSalaryComponentId(null);
      await loadCompanySalary();
      setMessage('Salary component saved.');
    } catch (error) {
      setMessage(errorText(error));
    } finally {
      setIsLoading(false);
    }
  };

  const submitSalaryStructure = async (event) => {
    event.preventDefault();
    setIsLoading(true);
    clearMessage();
    try {
      const payload = {
        ...salaryStructureForm,
        effectiveTo: salaryStructureForm.effectiveTo || null,
        components: salaryStructureForm.components.map((line) => ({
          componentId: Number(line.componentId),
          value: Number(line.value),
          percentageBaseComponentId: line.percentageBaseComponentId
            ? Number(line.percentageBaseComponentId) : null
        }))
      };
      if (editingSalaryStructureId) {
        await api.put(`/company-admin/salary/structures/${editingSalaryStructureId}`, payload);
      } else {
        await api.post('/company-admin/salary/structures', payload);
      }
      setSalaryStructureForm({ code: '', name: '', effectiveFrom: today, effectiveTo: '', components: [] });
      setEditingSalaryStructureId(null);
      await loadCompanySalary();
      setMessage('Salary structure saved. Amounts shown are illustrative, not payroll calculations.');
    } catch (error) {
      setMessage(errorText(error));
    } finally {
      setIsLoading(false);
    }
  };

  const addSalaryStructureLine = () => {
    if (!salaryLineForm.componentId || salaryLineForm.value === '') {
      setMessage('Choose a component and enter its value first.');
      return;
    }
    if (salaryStructureForm.components.some((line) => String(line.componentId) === salaryLineForm.componentId)) {
      setMessage('Each salary component can only be added once.');
      return;
    }
    setSalaryStructureForm((current) => ({
      ...current,
      components: [...current.components, { ...salaryLineForm }]
    }));
    setSalaryLineForm({ componentId: '', value: '', percentageBaseComponentId: '' });
    clearMessage();
  };

  const editSalaryComponent = (component) => {
    setEditingSalaryComponentId(component.id);
    setSalaryComponentForm({
      code: component.code, name: component.name, type: component.type,
      calculationMethod: component.calculationMethod, taxable: component.taxable,
      effectiveFrom: component.effectiveFrom, effectiveTo: component.effectiveTo || ''
    });
    window.scrollTo({ top: 0, behavior: 'smooth' });
  };

  const editSalaryStructure = async (structure) => {
    setIsLoading(true);
    clearMessage();
    try {
      const { data } = await api.get(`/company-admin/salary/structures/${structure.id}`);
      setEditingSalaryStructureId(data.id);
      setSalaryStructureForm({
        code: data.code,
        name: data.name,
        effectiveFrom: data.effectiveFrom,
        effectiveTo: data.effectiveTo || '',
        components: data.components.map((line) => ({
          componentId: String(line.componentId),
          value: String(line.value),
          percentageBaseComponentId: String(salaryComponents.find((item) => item.code === line.percentageBaseCode)?.id || '')
        }))
      });
    } catch (error) {
      setMessage(errorText(error));
    } finally {
      setIsLoading(false);
    }
  };

  const toggleSalaryStatus = async (kind, item) => {
    const active = !item.active;
    if (!window.confirm(`${active ? 'Activate' : 'Deactivate'} ${item.name}?`)) return;
    setIsLoading(true);
    clearMessage();
    try {
      await api.patch(`/company-admin/salary/${kind}/${item.id}/status`, { active });
      await loadCompanySalary();
    } catch (error) {
      setMessage(errorText(error));
    } finally {
      setIsLoading(false);
    }
  };

  const submitSalaryAssignment = async (event) => {
    event.preventDefault();
    if (!window.confirm('Assign this salary structure and retain the existing salary history?')) return;
    setIsLoading(true);
    clearMessage();
    try {
      await api.post('/company-admin/salary/assignments', {
        ...salaryAssignmentForm,
        employeeId: Number(salaryAssignmentForm.employeeId),
        salaryStructureId: Number(salaryAssignmentForm.salaryStructureId),
        effectiveTo: salaryAssignmentForm.effectiveTo || null
      });
      await loadSalaryHistory(salaryAssignmentForm.employeeId);
      setMessage('Salary assignment saved.');
    } catch (error) {
      setMessage(errorText(error));
    } finally {
      setIsLoading(false);
    }
  };

  const recordSelfAttendance = async (action) => {
    setIsLoading(true);
    clearMessage();
    try {
      await api.post(`/employee/attendance/${action}`);
      setMessage(action === 'check-in' ? 'Check-in recorded.' : 'Check-out recorded.');
      await loadMyAttendance();
    } catch (error) {
      setMessage(errorText(error));
    } finally {
      setIsLoading(false);
    }
  };

  const submitAttendance = async (event) => {
    event.preventDefault();
    const prompt = editingAttendanceId
      ? 'Submit this attendance correction for approval?'
      : 'Create this manual attendance record for approval?';
    if (!window.confirm(prompt)) return;
    setIsLoading(true);
    clearMessage();
    const payload = {
      status: attendanceForm.status,
      checkInTime: attendanceForm.checkInTime || null,
      checkOutTime: attendanceForm.checkOutTime || null,
      notes: attendanceForm.notes
    };
    try {
      if (editingAttendanceId) {
        await api.put(`/company-admin/attendance/${editingAttendanceId}/correction`, payload);
        setMessage('Correction submitted for approval.');
      } else {
        await api.post('/company-admin/attendance/manual', {
          ...payload,
          employeeId: Number(attendanceForm.employeeId),
          date: attendanceForm.date
        });
        setMessage('Manual attendance submitted for approval.');
      }
      setView('attendance');
      await loadCompanyAttendance(0);
    } catch (error) {
      setMessage(errorText(error));
    } finally {
      setIsLoading(false);
    }
  };

  const approveAttendance = async (record, decision = 'APPROVE') => {
    const action = decision === 'APPROVE' ? 'Approve' : 'Reject';
    if (!window.confirm(`${action} attendance for ${record.employeeName} on ${record.date}?`)) return;
    setIsLoading(true);
    clearMessage();
    try {
      await api.patch(`/company-admin/attendance/${record.id}/approval`, { decision });
      setMessage(`Attendance ${decision.toLowerCase()}d.`);
      await loadCompanyAttendance(attendancePage.number);
    } catch (error) {
      setMessage(errorText(error));
    } finally {
      setIsLoading(false);
    }
  };

  const submitAttendanceRules = async (event) => {
    event.preventDefault();
    setIsLoading(true);
    clearMessage();
    try {
      const { data } = await api.put('/company-admin/attendance/rules', attendanceRules);
      setAttendanceRules({
        ...data,
        workStartTime: data.workStartTime.slice(0, 5),
        workEndTime: data.workEndTime.slice(0, 5)
      });
      setMessage('Attendance rules saved.');
    } catch (error) {
      setMessage(errorText(error));
    } finally {
      setIsLoading(false);
    }
  };

  const loadPlatformAttendance = async (event) => {
    if (event) event.preventDefault();
    if (!platformAttendanceCompanyId) {
      setMessage('Choose a company to view attendance.');
      return;
    }
    setIsLoading(true);
    clearMessage();
    try {
      const { data } = await api.get('/super-admin/attendance', {
        params: { companyId: platformAttendanceCompanyId, date: platformAttendanceDate }
      });
      setPlatformAttendance(data);
    } catch (error) {
      setMessage(errorText(error));
    } finally {
      setIsLoading(false);
    }
  };

  useEffect(() => {
    if (token && canManage && view === 'employees') {
      loadOrganization();
      loadEmployees(0);
    }
  }, [token, canManage, view]);

  useEffect(() => {
    if (token && canManage && view === 'employee-form') {
      api.get('/company-admin/employees', { params: { status: 'ACTIVE', page: 0, size: 100 } })
        .then((response) => setManagerEmployees(response.data.content))
        .catch((error) => setMessage(errorText(error)));
    }
  }, [token, canManage, view]);

  useEffect(() => {
    if (token && user?.role === 'SUPER_ADMIN' && view === 'super-admin') {
      Promise.all([api.get('/super-admin/dashboard'), api.get('/super-admin/companies')])
        .then(([dashboardResponse, companiesResponse]) => {
          setStats(dashboardResponse.data);
          setCompanies(companiesResponse.data);
          setMessage('');
        })
        .catch((error) => setMessage(errorText(error)));
    }
  }, [token, user, view]);

  useEffect(() => {
    if (token && user?.role === 'EMPLOYEE' && view === 'my-profile') {
      setIsLoading(true);
      api.get('/employee/me/profile')
        .then((response) => setSelectedEmployee(response.data))
        .catch((error) => setMessage(errorText(error)))
        .finally(() => setIsLoading(false));
    }
  }, [token, user, view]);

  useEffect(() => {
    if (token && canManage && view === 'attendance') {
      loadAttendanceEmployees();
      loadCompanyAttendance(0);
    }
  }, [token, canManage, view]);

  useEffect(() => {
    if (token && canManage && view === 'attendance-rules') loadAttendanceRules();
  }, [token, canManage, view]);

  useEffect(() => {
    if (token && canManage && view === 'attendance-form') loadAttendanceEmployees();
  }, [token, canManage, view]);

  useEffect(() => {
    if (token && user?.role === 'EMPLOYEE' && view === 'my-attendance') loadMyAttendance();
  }, [token, user, view]);

  useEffect(() => {
    if (token && user?.role === 'EMPLOYEE' && view === 'my-leave') loadEmployeeLeave();
  }, [token, user, view, leaveYear]);

  useEffect(() => {
    if (token && canManage && view === 'leave-management') loadCompanyLeave(0);
  }, [token, canManage, view]);

  useEffect(() => {
    if (token && canManage && view === 'salary-management') loadCompanySalary();
  }, [token, canManage, view]);

  useEffect(() => {
    if (token && user?.role === 'EMPLOYEE' && view === 'my-salary') loadEmployeeSalary();
  }, [token, user, view]);

  useEffect(() => {
    if (token && user?.role === 'SUPER_ADMIN' && view === 'salary-platform') loadPlatformSalary();
  }, [token, user, view]);

  useEffect(() => {
    if (token && canManage && view === 'salary-management' && salaryEmployeeId) {
      loadSalaryHistory(salaryEmployeeId);
    }
  }, [token, canManage, view, salaryEmployeeId]);

  useEffect(() => {
    if (user?.role === 'SUPER_ADMIN' && companies.length && !platformAttendanceCompanyId) {
      setPlatformAttendanceCompanyId(String(companies[0].id));
    }
  }, [user, companies, platformAttendanceCompanyId]);

  const handleLogin = async (event) => {
    event.preventDefault();
    setIsLoading(true);
    clearMessage();
    try {
      const { data } = await api.post('/auth/login', loginForm);
      const userPayload = {
        username: data.username,
        role: data.role,
        tenantId: data.tenantId,
        companyId: data.companyId
      };
      localStorage.setItem('payrollToken', data.token);
      localStorage.setItem('payrollUser', JSON.stringify(userPayload));
      setToken(data.token);
      setUser(userPayload);
      setView(data.role === 'SUPER_ADMIN' ? 'super-admin' : data.role === 'EMPLOYEE' ? 'my-attendance' : 'employees');
    } catch (error) {
      setMessage(errorText(error));
    } finally {
      setIsLoading(false);
    }
  };

  const handleLogout = async () => {
    let logoutFailed = false;
    try {
      await api.post('/auth/logout');
    } catch {
      logoutFailed = true;
    } finally {
      localStorage.removeItem('payrollToken');
      localStorage.removeItem('payrollUser');
      setToken('');
      setUser(null);
      setSelectedEmployee(null);
      setView('login');
      if (logoutFailed) setMessage('The session was cleared locally, but the server could not confirm logout.');
    }
  };

  const handleForgotPassword = async (event) => {
    event.preventDefault();
    setIsLoading(true);
    clearMessage();
    try {
      const { data } = await api.post('/auth/forgot-password', { email: forgotEmail });
      setMessage(data.message || 'If the email is registered, reset instructions will be sent.');
    } catch (error) {
      setMessage(errorText(error));
    } finally {
      setIsLoading(false);
    }
  };

  const handleResetPassword = async (event) => {
    event.preventDefault();
    setIsLoading(true);
    clearMessage();
    try {
      const { data } = await api.post('/auth/reset-password', resetForm);
      setMessage(data.message || 'Password reset successfully.');
      setResetForm({ token: '', newPassword: '' });
    } catch (error) {
      setMessage(errorText(error));
    } finally {
      setIsLoading(false);
    }
  };

  const handleCompanyRegistration = async (event) => {
    event.preventDefault();
    setIsLoading(true);
    clearMessage();
    try {
      await api.post('/super-admin/companies', companyForm);
      setMessage('Company registered successfully.');
      setCompanyForm({
        companyName: '', companyCode: '', companyEmail: '', companyPhone: '',
        companyAddress: '', subscriptionPlan: 'PRO', adminUsername: '', adminPassword: ''
      });
      setView('super-admin');
    } catch (error) {
      setMessage(errorText(error));
    } finally {
      setIsLoading(false);
    }
  };

  const openNewEmployee = () => {
    setSelectedEmployee(null);
    setEditingEmployeeId(null);
    setEmployeeForm(emptyEmployee);
    setView('employee-form');
    clearMessage();
  };

  const openEditEmployee = async (employee) => {
    setIsLoading(true);
    clearMessage();
    try {
      const { data } = await api.get(`/company-admin/employees/${employee.id}`);
      setSelectedEmployee(null);
      setEditingEmployeeId(data.id);
      setEmployeeForm({
        ...emptyEmployee,
        ...data,
        dateOfBirth: data.dateOfBirth || '',
        dateOfJoining: data.dateOfJoining || '',
        reportingManagerId: data.reportingManagerId || '',
        departmentId: data.departmentId || '',
        designationId: data.designationId || ''
      });
      setView('employee-form');
    } catch (error) {
      setMessage(errorText(error));
    } finally {
      setIsLoading(false);
    }
  };

  const handleEmployeeSubmit = async (event) => {
    event.preventDefault();
    setIsLoading(true);
    clearMessage();
    const payload = {
      ...employeeForm,
      dateOfBirth: employeeForm.dateOfBirth || null,
      departmentId: Number(employeeForm.departmentId),
      designationId: Number(employeeForm.designationId),
      reportingManagerId: employeeForm.reportingManagerId ? Number(employeeForm.reportingManagerId) : null
    };
    try {
      if (editingEmployeeId) {
        await api.put(`/company-admin/employees/${editingEmployeeId}`, payload);
        setMessage('Employee details updated.');
      } else {
        await api.post('/company-admin/employees', payload);
        setMessage('Employee added successfully.');
      }
      setView('employees');
      await loadEmployees(0);
    } catch (error) {
      setMessage(errorText(error));
    } finally {
      setIsLoading(false);
    }
  };

  const openEmployeeDetails = async (employee) => {
    setIsLoading(true);
    clearMessage();
    try {
      const { data } = await api.get(`/company-admin/employees/${employee.id}`);
      setSelectedEmployee(data);
      setView('employee-details');
    } catch (error) {
      setMessage(errorText(error));
    } finally {
      setIsLoading(false);
    }
  };

  const openNewAttendance = () => {
    setEditingAttendanceId(null);
    setAttendanceForm(emptyAttendance);
    setView('attendance-form');
    clearMessage();
  };

  const openAttendanceCorrection = (record) => {
    setEditingAttendanceId(record.id);
    setAttendanceForm({
      employeeId: String(record.employeeId),
      date: record.date,
      status: record.status,
      checkInTime: record.checkInTime?.slice(11, 16) || '',
      checkOutTime: record.checkOutTime?.slice(11, 16) || '',
      notes: record.notes || ''
    });
    setView('attendance-form');
    clearMessage();
  };

  const changeEmployeeStatus = async (employee) => {
    const nextStatus = employee.status === 'ACTIVE' ? 'INACTIVE' : 'ACTIVE';
    if (!window.confirm(`${nextStatus === 'ACTIVE' ? 'Activate' : 'Deactivate'} ${employee.firstName} ${employee.lastName}?`)) return;
    setIsLoading(true);
    clearMessage();
    try {
      await api.patch(`/company-admin/employees/${employee.id}/status`, { status: nextStatus });
      setMessage(`Employee ${nextStatus.toLowerCase()}.`);
      await loadEmployees(employees.number);
    } catch (error) {
      setMessage(errorText(error));
    } finally {
      setIsLoading(false);
    }
  };

  const loadOrgRows = async () => {
    setIsLoading(true);
    clearMessage();
    try {
      const { data } = await api.get(employeeManagementUrl);
      orgKind === 'departments' ? setDepartments(data) : setDesignations(data);
    } catch (error) {
      setMessage(errorText(error));
    } finally {
      setIsLoading(false);
    }
  };

  useEffect(() => {
    if (token && canManage && (view === 'departments' || view === 'designations')) loadOrgRows();
  }, [token, canManage, view, employeeManagementUrl]);

  const submitOrg = async (event) => {
    event.preventDefault();
    setIsLoading(true);
    clearMessage();
    try {
      const base = `/company-admin/${orgKind}`;
      if (orgForm.id) await api.put(`${base}/${orgForm.id}`, { name: orgForm.name });
      else await api.post(base, { name: orgForm.name });
      setOrgForm({ name: '', id: null });
      await loadOrgRows();
      await loadOrganization();
    } catch (error) {
      setMessage(errorText(error));
    } finally {
      setIsLoading(false);
    }
  };

  const toggleOrg = async (item) => {
    const action = item.active ? 'Deactivate' : 'Activate';
    if (!window.confirm(`${action} ${item.name}?`)) return;
    setIsLoading(true);
    clearMessage();
    try {
      await api.patch(`/company-admin/${orgKind}/${item.id}/status`, { active: !item.active });
      await loadOrgRows();
      await loadOrganization();
    } catch (error) {
      setMessage(errorText(error));
    } finally {
      setIsLoading(false);
    }
  };

  const viewPlatformEmployees = async (company) => {
    setIsLoading(true);
    clearMessage();
    try {
      const { data } = await api.get(`/super-admin/companies/${company.id}/employees`);
      setPlatformCompany(company);
      setPlatformEmployees(data.content);
    } catch (error) {
      setMessage(errorText(error));
    } finally {
      setIsLoading(false);
    }
  };

  const renderLogin = () => (
    <div className="auth-layout">
      <div className="auth-panel left-panel">
        <span className="eyebrow">Payroll SaaS</span>
        <h1>Secure employee operations for modern organizations.</h1>
        <ul><li>Tenant-isolated workforce management</li><li>Role-aware access</li><li>Employee lifecycle tools</li></ul>
      </div>
      <div className="auth-panel form-panel">
        <h2>Sign in</h2>
        <form onSubmit={handleLogin}>
          <label>Email<input required type="email" autoComplete="username" value={loginForm.email} onChange={(event) => setLoginForm({ ...loginForm, email: event.target.value })} /></label>
          <label>Password<input required type="password" autoComplete="current-password" value={loginForm.password} onChange={(event) => setLoginForm({ ...loginForm, password: event.target.value })} /></label>
          <button className="primary-btn" disabled={isLoading}>{isLoading ? 'Signing in…' : 'Login'}</button>
        </form>
        <div className="auth-actions">
          <button className="link-btn" onClick={() => { setView('forgot-password'); clearMessage(); }}>Forgot password?</button>
          <button className="link-btn" onClick={() => { setView('reset-password'); clearMessage(); }}>Reset password</button>
        </div>
        {message && <p className="message" role="alert">{message}</p>}
      </div>
    </div>
  );

  const renderForgotPassword = () => (
    <div className="auth-panel form-panel small-panel">
      <h2>Forgot password</h2>
      <form onSubmit={handleForgotPassword}>
        <label>Email<input required type="email" value={forgotEmail} onChange={(event) => setForgotEmail(event.target.value)} /></label>
        <button className="primary-btn" disabled={isLoading}>{isLoading ? 'Sending…' : 'Send reset request'}</button>
      </form>
      {message && <p className="message" role="status">{message}</p>}
      <button className="link-btn" onClick={() => setView('login')}>Back to login</button>
    </div>
  );

  const renderResetPassword = () => (
    <div className="auth-panel form-panel small-panel">
      <h2>Reset password</h2>
      <form onSubmit={handleResetPassword}>
        <label>Reset token<input required value={resetForm.token} onChange={(event) => setResetForm({ ...resetForm, token: event.target.value })} /></label>
        <label>New password<input required minLength="10" type="password" value={resetForm.newPassword} onChange={(event) => setResetForm({ ...resetForm, newPassword: event.target.value })} /></label>
        <button className="primary-btn" disabled={isLoading}>{isLoading ? 'Resetting…' : 'Reset password'}</button>
      </form>
      {message && <p className="message" role="alert">{message}</p>}
      <button className="link-btn" onClick={() => setView('login')}>Back to login</button>
    </div>
  );

  const renderHeader = () => (
    <header className="topbar">
      <div><span className="eyebrow">{user?.role?.replace('_', ' ')}</span><h1>{view === 'super-admin' ? 'Platform overview' : 'Employee workspace'}</h1></div>
      <div className="topbar-actions"><span className="user-label">{user?.username}</span><button className="secondary-btn" onClick={handleLogout}>Logout</button></div>
    </header>
  );

  const renderCompanyNavigation = () => (
    <nav className="workspace-nav" aria-label="Company workspace">
      <button className={view.startsWith('employee') || view === 'employees' ? 'nav-active' : ''} onClick={() => { setView('employees'); setSelectedEmployee(null); }}>Employees</button>
      <button className={view === 'departments' ? 'nav-active' : ''} onClick={() => { setOrgKind('departments'); setView('departments'); setOrgForm({ name: '', id: null }); }}>Departments</button>
      <button className={view === 'designations' ? 'nav-active' : ''} onClick={() => { setOrgKind('designations'); setView('designations'); setOrgForm({ name: '', id: null }); }}>Designations</button>
      <button className={view.startsWith('attendance') ? 'nav-active' : ''} onClick={() => { clearMessage(); setView('attendance'); }}>Attendance</button>
      <button className={view === 'attendance-rules' ? 'nav-active' : ''} onClick={() => { clearMessage(); setView('attendance-rules'); }}>Attendance rules</button>
      <button className={view === 'leave-management' ? 'nav-active' : ''} onClick={() => { clearMessage(); setView('leave-management'); }}>Leave</button>
      <button className={view === 'salary-management' ? 'nav-active' : ''} onClick={() => { clearMessage(); setView('salary-management'); }}>Salary structures</button>
      <button className={view === 'payroll' ? 'nav-active' : ''} onClick={() => { clearMessage(); setView('payroll'); }}>Payroll</button>
      <button className={view === 'payslips' ? 'nav-active' : ''} onClick={() => { clearMessage(); setView('payslips'); }}>Payslips</button>
    </nav>
  );

  const renderEmployeeList = () => (
    <>
      <div className="page-heading"><div><h2>Employees</h2><p>Manage your company’s employee profiles.</p></div><button className="primary-btn" onClick={openNewEmployee}>Add employee</button></div>
      <form className="filter-bar" onSubmit={(event) => { event.preventDefault(); loadEmployees(0); }}>
        <label className="search-field">Search<input placeholder="Name or employee number" value={filters.search} onChange={(event) => setFilters({ ...filters, search: event.target.value })} /></label>
        <label>Department<select value={filters.departmentId} onChange={(event) => setFilters({ ...filters, departmentId: event.target.value })}><option value="">All departments</option>{departments.filter((item) => item.active).map((item) => <option key={item.id} value={item.id}>{item.name}</option>)}</select></label>
        <label>Designation<select value={filters.designationId} onChange={(event) => setFilters({ ...filters, designationId: event.target.value })}><option value="">All designations</option>{designations.filter((item) => item.active).map((item) => <option key={item.id} value={item.id}>{item.name}</option>)}</select></label>
        <label>Employment type<select value={filters.employmentType} onChange={(event) => setFilters({ ...filters, employmentType: event.target.value })}><option value="">All types</option><option>FULL_TIME</option><option>PART_TIME</option><option>CONTRACT</option><option>INTERN</option><option>TEMPORARY</option></select></label>
        <label>Status<select value={filters.status} onChange={(event) => setFilters({ ...filters, status: event.target.value })}><option value="">All statuses</option><option>ACTIVE</option><option>INACTIVE</option></select></label>
        <button className="secondary-btn" type="submit" disabled={isLoading}>Apply filters</button>
      </form>
      {message && <p className="message" role="alert">{message}</p>}
      {isLoading ? <p className="loading-state">Loading employees…</p> : employees.content.length === 0 ? <div className="empty-state"><h3>No employees found</h3><p>Try changing your filters or add your first employee.</p></div> : (
        <div className="table-wrap">
          <table><thead><tr><th>Employee</th><th>Number</th><th>Department</th><th>Designation</th><th>Status</th><th>Actions</th></tr></thead>
            <tbody>{employees.content.map((employee) => <tr key={employee.id}>
              <td><strong>{employee.firstName} {employee.lastName}</strong><small>{employee.email}</small></td>
              <td>{employee.employeeNumber}</td><td>{employee.departmentName}</td><td>{employee.designationName}</td>
              <td><span className={`status-pill ${employee.status.toLowerCase()}`}>{employee.status}</span></td>
              <td className="row-actions">
                <button className="table-btn" onClick={() => openEmployeeDetails(employee)}>View</button>
                <button className="table-btn" onClick={() => openEditEmployee(employee)}>Edit</button>
                <button className="table-btn" onClick={() => changeEmployeeStatus(employee)}>{employee.status === 'ACTIVE' ? 'Deactivate' : 'Activate'}</button>
              </td>
            </tr>)}</tbody>
          </table>
        </div>
      )}
      <div className="pagination">
        <span>{employees.totalElements} employee(s)</span>
        <div><button className="secondary-btn" disabled={isLoading || employees.number <= 0} onClick={() => loadEmployees(employees.number - 1)}>Previous</button>
          <span>Page {employees.totalPages ? employees.number + 1 : 0} of {employees.totalPages}</span>
          <button className="secondary-btn" disabled={isLoading || employees.number + 1 >= employees.totalPages} onClick={() => loadEmployees(employees.number + 1)}>Next</button></div>
      </div>
    </>
  );

  const renderEmployeeForm = () => (
    <>
      <div className="page-heading"><div><h2>{editingEmployeeId ? 'Edit employee' : 'Add employee'}</h2><p>Employee records are created without login credentials.</p></div><button className="secondary-btn" onClick={() => setView('employees')}>Back to list</button></div>
      {message && <p className="message" role="alert">{message}</p>}
      <form className="employee-form" onSubmit={handleEmployeeSubmit}>
        <section><h3>Personal details</h3><div className="form-grid">
          <label>Employee number<input required maxLength="40" value={employeeForm.employeeNumber} onChange={(event) => setEmployeeForm({ ...employeeForm, employeeNumber: event.target.value })} /></label>
          <label>First name<input required value={employeeForm.firstName} onChange={(event) => setEmployeeForm({ ...employeeForm, firstName: event.target.value })} /></label>
          <label>Last name<input required value={employeeForm.lastName} onChange={(event) => setEmployeeForm({ ...employeeForm, lastName: event.target.value })} /></label>
          <label>Email<input required type="email" value={employeeForm.email} onChange={(event) => setEmployeeForm({ ...employeeForm, email: event.target.value })} /></label>
          <label>Phone<input required value={employeeForm.phone} onChange={(event) => setEmployeeForm({ ...employeeForm, phone: event.target.value })} /></label>
          <label>Date of birth<input type="date" value={employeeForm.dateOfBirth} onChange={(event) => setEmployeeForm({ ...employeeForm, dateOfBirth: event.target.value })} /></label>
          <label>Gender<select value={employeeForm.gender} onChange={(event) => setEmployeeForm({ ...employeeForm, gender: event.target.value })}><option value="">Prefer not to specify</option><option value="FEMALE">Female</option><option value="MALE">Male</option><option value="NON_BINARY">Non-binary</option><option value="OTHER">Other</option><option value="PREFER_NOT_TO_SAY">Prefer not to say</option></select></label>
          <label className="wide-field">Address<input required value={employeeForm.address} onChange={(event) => setEmployeeForm({ ...employeeForm, address: event.target.value })} /></label>
        </div></section>
        <section><h3>Employment details</h3><div className="form-grid">
          <label>Job title<input required value={employeeForm.jobTitle} onChange={(event) => setEmployeeForm({ ...employeeForm, jobTitle: event.target.value })} /></label>
          <label>Department<select required value={employeeForm.departmentId} onChange={(event) => setEmployeeForm({ ...employeeForm, departmentId: event.target.value })}><option value="">Select department</option>{departments.filter((item) => item.active).map((item) => <option key={item.id} value={item.id}>{item.name}</option>)}</select></label>
          <label>Designation<select required value={employeeForm.designationId} onChange={(event) => setEmployeeForm({ ...employeeForm, designationId: event.target.value })}><option value="">Select designation</option>{designations.filter((item) => item.active).map((item) => <option key={item.id} value={item.id}>{item.name}</option>)}</select></label>
          <label>Employment type<select required value={employeeForm.employmentType} onChange={(event) => setEmployeeForm({ ...employeeForm, employmentType: event.target.value })}><option value="FULL_TIME">Full-time</option><option value="PART_TIME">Part-time</option><option value="CONTRACT">Contract</option><option value="INTERN">Intern</option><option value="TEMPORARY">Temporary</option></select></label>
          <label>Date of joining<input required type="date" value={employeeForm.dateOfJoining} onChange={(event) => setEmployeeForm({ ...employeeForm, dateOfJoining: event.target.value })} /></label>
          <label>Reporting manager<select value={employeeForm.reportingManagerId} onChange={(event) => setEmployeeForm({ ...employeeForm, reportingManagerId: event.target.value })}><option value="">No manager</option>{managerEmployees.filter((item) => item.id !== editingEmployeeId).map((item) => <option key={item.id} value={item.id}>{item.firstName} {item.lastName} · {item.employeeNumber}</option>)}</select></label>
          <label>Status<select value={employeeForm.status} onChange={(event) => setEmployeeForm({ ...employeeForm, status: event.target.value })}><option value="ACTIVE">Active</option><option value="INACTIVE">Inactive</option></select></label>
        </div></section>
        <section><h3>Emergency contact <span className="optional-label">Optional</span></h3><div className="form-grid">
          <label>Name<input value={employeeForm.emergencyContactName || ''} onChange={(event) => setEmployeeForm({ ...employeeForm, emergencyContactName: event.target.value })} /></label>
          <label>Phone<input value={employeeForm.emergencyContactPhone || ''} onChange={(event) => setEmployeeForm({ ...employeeForm, emergencyContactPhone: event.target.value })} /></label>
          <label>Relationship<input value={employeeForm.emergencyContactRelationship || ''} onChange={(event) => setEmployeeForm({ ...employeeForm, emergencyContactRelationship: event.target.value })} /></label>
        </div></section>
        <div className="form-actions"><button className="secondary-btn" type="button" onClick={() => setView('employees')}>Cancel</button><button className="primary-btn" disabled={isLoading}>{isLoading ? 'Saving…' : editingEmployeeId ? 'Save changes' : 'Add employee'}</button></div>
      </form>
    </>
  );

  const renderEmployeeDetails = (employee) => (
    <>
      <div className="page-heading"><div><h2>Employee profile</h2><p>{employee.employeeNumber}</p></div><div className="topbar-actions"><button className="secondary-btn" onClick={() => setView('employees')}>Back</button>{canManage && <button className="primary-btn" onClick={() => openEditEmployee(employee)}>Edit profile</button>}</div></div>
      {message && <p className="message" role="alert">{message}</p>}
      <div className="profile-card">
        <div className="profile-title"><div className="avatar">{employee.firstName?.[0]}{employee.lastName?.[0]}</div><div><h3>{employee.firstName} {employee.lastName}</h3><span>{employee.jobTitle} · {employee.departmentName || 'No department'}</span></div><span className={`status-pill ${employee.status?.toLowerCase()}`}>{employee.status}</span></div>
        <div className="profile-grid">
          <ProfileField label="Email" value={employee.email} /><ProfileField label="Phone" value={employee.phone} />
          <ProfileField label="Employee number" value={employee.employeeNumber} /><ProfileField label="Designation" value={employee.designationName} />
          <ProfileField label="Employment type" value={employee.employmentType} /><ProfileField label="Date joined" value={employee.dateOfJoining} />
          <ProfileField label="Date of birth" value={employee.dateOfBirth} /><ProfileField label="Gender" value={employee.gender} />
          <ProfileField label="Address" value={employee.address} /><ProfileField label="Reporting manager ID" value={employee.reportingManagerId} />
          <ProfileField label="Emergency contact" value={employee.emergencyContactName} /><ProfileField label="Emergency phone" value={employee.emergencyContactPhone} />
        </div>
      </div>
    </>
  );

  const renderOrgManagement = () => {
    const isDepartment = orgKind === 'departments';
    const rows = isDepartment ? departments : designations;
    const kindLabel = isDepartment ? 'Department' : 'Designation';
    return <>
      <div className="page-heading"><div><h2>{kindLabel}s</h2><p>Manage active organizational options for employee assignment.</p></div></div>
      <form className="org-form" onSubmit={submitOrg}>
        <label>{orgForm.id ? `Edit ${kindLabel.toLowerCase()}` : `New ${kindLabel.toLowerCase()}`}<input required maxLength="120" value={orgForm.name} onChange={(event) => setOrgForm({ ...orgForm, name: event.target.value })} /></label>
        <button className="primary-btn" disabled={isLoading}>{orgForm.id ? 'Save name' : `Add ${kindLabel.toLowerCase()}`}</button>
        {orgForm.id && <button type="button" className="secondary-btn" onClick={() => setOrgForm({ name: '', id: null })}>Cancel edit</button>}
      </form>
      {message && <p className="message" role="alert">{message}</p>}
      {isLoading ? <p className="loading-state">Loading {kindLabel.toLowerCase()}s…</p> : rows.length === 0 ? <div className="empty-state"><h3>No {kindLabel.toLowerCase()}s yet</h3><p>Add one above to assign it to employees.</p></div> : (
        <div className="table-wrap"><table><thead><tr><th>Name</th><th>Status</th><th>Actions</th></tr></thead><tbody>
          {rows.map((item) => <tr key={item.id}><td>{item.name}</td><td><span className={`status-pill ${item.active ? 'active' : 'inactive'}`}>{item.active ? 'ACTIVE' : 'INACTIVE'}</span></td>
            <td className="row-actions"><button className="table-btn" onClick={() => setOrgForm({ name: item.name, id: item.id })}>Edit</button><button className="table-btn" onClick={() => toggleOrg(item)}>{item.active ? 'Deactivate' : 'Activate'}</button></td>
          </tr>)}
        </tbody></table></div>
      )}
    </>;
  };

  const renderAttendanceTable = (rows, showActions = false) => rows.length === 0
    ? <div className="empty-state"><h3>No attendance records</h3><p>Change the date or filters, or record attendance manually.</p></div>
    : <div className="table-wrap"><table><thead><tr>
      <th>Employee</th><th>Date</th><th>Check-in</th><th>Check-out</th><th>Minutes</th><th>Status</th><th>Flags</th><th>Approval</th>{showActions && <th>Actions</th>}
    </tr></thead><tbody>{rows.map((record) => <tr key={record.id}>
      <td><strong>{record.employeeName}</strong><small>{record.employeeNumber}</small></td>
      <td>{record.date}</td>
      <td>{record.checkInTime ? <>{record.checkInTime.slice(11, 16)}<small>{record.timeZoneId}</small></> : '—'}</td>
      <td>{record.checkOutTime ? <>{record.checkOutTime.slice(11, 16)}<small>{record.timeZoneId}</small></> : '—'}</td>
      <td>{record.totalWorkingMinutes}</td>
      <td><span className={`status-pill ${record.status.toLowerCase()}`}>{record.status}</span></td>
      <td>{[record.lateArrival && 'Late arrival', record.earlyDeparture && 'Early departure', record.overtimeMinutes > 0 && `${record.overtimeMinutes}m OT`].filter(Boolean).join(' · ') || '—'}</td>
      <td><span className={`status-pill ${record.approvalStatus.toLowerCase()}`}>{record.approvalStatus}</span></td>
      {showActions && <td className="row-actions">
        <button className="table-btn" onClick={() => openAttendanceCorrection(record)}>Correct</button>
        {record.approvalStatus === 'PENDING' && <>
          <button className="table-btn" onClick={() => approveAttendance(record, 'APPROVE')}>Approve</button>
          <button className="table-btn" onClick={() => approveAttendance(record, 'REJECT')}>Reject</button>
        </>}
      </td>}
    </tr>)}</tbody></table></div>;

  const renderAttendanceSummary = (summary) => summary && <div className="dashboard-grid attendance-summary">
    <Stat label="Present" value={summary.presentDays} />
    <Stat label="Absent" value={summary.absentDays} />
    <Stat label="Half days" value={summary.halfDays} />
    <Stat label="Late arrivals" value={summary.lateArrivals} />
    <Stat label="Leave" value={summary.leaveDays} />
    <Stat label="Working hours" value={`${Math.floor(summary.totalWorkingMinutes / 60)}h ${summary.totalWorkingMinutes % 60}m`} />
    <Stat label="Overtime" value={`${Math.floor(summary.overtimeMinutes / 60)}h ${summary.overtimeMinutes % 60}m`} />
  </div>;

  const renderAttendanceWorkspace = () => (
    <>
      <div className="page-heading">
        <div><h2>Attendance</h2><p>Daily records, approvals, and company attendance totals.</p></div>
        <button className="primary-btn" onClick={openNewAttendance}>Add manual record</button>
      </div>
      <form className="filter-bar attendance-filters" onSubmit={(event) => { event.preventDefault(); loadCompanyAttendance(0); }}>
        <label>Date<input type="date" required value={attendanceFilters.date} onChange={(event) => setAttendanceFilters({ ...attendanceFilters, date: event.target.value })} /></label>
        <label className="search-field">Employee<input placeholder="Name or employee number" value={attendanceFilters.search} onChange={(event) => setAttendanceFilters({ ...attendanceFilters, search: event.target.value })} /></label>
        <label>Department<select value={attendanceFilters.departmentId} onChange={(event) => setAttendanceFilters({ ...attendanceFilters, departmentId: event.target.value })}><option value="">All departments</option>{departments.filter((item) => item.active).map((item) => <option key={item.id} value={item.id}>{item.name}</option>)}</select></label>
        <label>Status<select value={attendanceFilters.status} onChange={(event) => setAttendanceFilters({ ...attendanceFilters, status: event.target.value })}><option value="">All statuses</option>{['PRESENT', 'ABSENT', 'HALF_DAY', 'LATE', 'ON_LEAVE', 'HOLIDAY', 'WEEK_OFF'].map((status) => <option key={status}>{status}</option>)}</select></label>
        <button className="secondary-btn" disabled={isLoading}>Apply filters</button>
      </form>
      {message && <p className="message" role="alert">{message}</p>}
      <section className="panel-section">
        <div className="section-heading"><h3>Daily attendance</h3><span>{attendancePage.totalElements || 0} records</span></div>
        {isLoading ? <p className="loading-state">Loading attendance…</p> : renderAttendanceTable(attendancePage.content, true)}
        <div className="pagination">
          <span>Page {attendancePage.totalPages ? attendancePage.number + 1 : 0} of {attendancePage.totalPages}</span>
          <div><button className="secondary-btn" disabled={isLoading || attendancePage.number <= 0} onClick={() => loadCompanyAttendance(attendancePage.number - 1)}>Previous</button>
            <button className="secondary-btn" disabled={isLoading || attendancePage.number + 1 >= attendancePage.totalPages} onClick={() => loadCompanyAttendance(attendancePage.number + 1)}>Next</button></div>
        </div>
      </section>
      <section className="panel-section attendance-monthly">
        <div className="page-heading"><div><h3>Monthly summary</h3><p>Status totals are exclusive; late arrival is a separate flag.</p></div>
          <label>Month<input type="month" value={attendanceMonth} onChange={(event) => setAttendanceMonth(event.target.value)} /></label>
          <button className="secondary-btn" onClick={() => loadCompanyAttendance(0)} disabled={isLoading}>Refresh summary</button>
        </div>
        {renderAttendanceSummary(attendanceSummary)}
        <h3>Employee report</h3>
        {attendanceReport.length === 0 ? <p className="empty-inline">No attendance for this month.</p> : <div className="table-wrap"><table><thead><tr><th>Employee</th><th>Days recorded</th><th>Days attended</th><th>Half days</th><th>Late arrivals</th><th>Working minutes</th><th>Overtime</th></tr></thead>
          <tbody>{attendanceReport.map((row) => <tr key={row.employeeId}><td>{row.employeeName}<small>{row.employeeNumber}</small></td><td>{row.recordedDays}</td><td>{row.presentDays}</td><td>{row.halfDays}</td><td>{row.lateArrivals}</td><td>{row.totalWorkingMinutes}</td><td>{row.overtimeMinutes}</td></tr>)}</tbody>
        </table></div>}
      </section>
    </>
  );

  const renderAttendanceForm = () => (
    <>
      <div className="page-heading"><div><h2>{editingAttendanceId ? 'Correct attendance' : 'Manual attendance'}</h2><p>Manual records and corrections require approval before being finalized.</p></div><button className="secondary-btn" onClick={() => setView('attendance')}>Back to attendance</button></div>
      {message && <p className="message" role="alert">{message}</p>}
      <form className="employee-form" onSubmit={submitAttendance}>
        <section><div className="form-grid">
          {!editingAttendanceId && <label>Employee<select required value={attendanceForm.employeeId} onChange={(event) => setAttendanceForm({ ...attendanceForm, employeeId: event.target.value })}><option value="">Choose employee</option>{attendanceEmployees.map((employee) => <option key={employee.id} value={employee.id}>{employee.firstName} {employee.lastName} · {employee.employeeNumber}</option>)}</select></label>}
          <label>Date<input required type="date" disabled={Boolean(editingAttendanceId)} value={attendanceForm.date} onChange={(event) => setAttendanceForm({ ...attendanceForm, date: event.target.value })} /></label>
          <label>Status<select value={attendanceForm.status} onChange={(event) => setAttendanceForm({ ...attendanceForm, status: event.target.value })}>{['PRESENT', 'ABSENT', 'HALF_DAY', 'LATE', 'ON_LEAVE', 'HOLIDAY', 'WEEK_OFF'].map((status) => <option key={status}>{status}</option>)}</select></label>
          <label>Check-in<input type="time" value={attendanceForm.checkInTime} onChange={(event) => setAttendanceForm({ ...attendanceForm, checkInTime: event.target.value })} /></label>
          <label>Check-out<input type="time" value={attendanceForm.checkOutTime} onChange={(event) => setAttendanceForm({ ...attendanceForm, checkOutTime: event.target.value })} /></label>
          <label className="wide-field">Notes<input maxLength="1000" value={attendanceForm.notes} onChange={(event) => setAttendanceForm({ ...attendanceForm, notes: event.target.value })} /></label>
        </div></section>
        <div className="form-actions"><button type="button" className="secondary-btn" onClick={() => setView('attendance')}>Cancel</button><button className="primary-btn" disabled={isLoading}>{isLoading ? 'Saving…' : editingAttendanceId ? 'Submit correction' : 'Create manual record'}</button></div>
      </form>
    </>
  );

  const renderAttendanceRules = () => (
    <>
      <div className="page-heading"><div><h2>Attendance rules</h2><p>Rules are stored per company and use the selected IANA timezone.</p></div></div>
      {message && <p className="message" role="alert">{message}</p>}
      {isLoading ? <p className="loading-state">Loading attendance rules…</p> : <form className="employee-form" onSubmit={submitAttendanceRules}>
        <section><div className="form-grid">
          <label>Work starts<input required type="time" value={attendanceRules.workStartTime} onChange={(event) => setAttendanceRules({ ...attendanceRules, workStartTime: event.target.value })} /></label>
          <label>Work ends<input required type="time" value={attendanceRules.workEndTime} onChange={(event) => setAttendanceRules({ ...attendanceRules, workEndTime: event.target.value })} /></label>
          <label>Required work minutes<input required type="number" min="1" value={attendanceRules.requiredWorkingMinutes} onChange={(event) => setAttendanceRules({ ...attendanceRules, requiredWorkingMinutes: Number(event.target.value) })} /></label>
          <label>Grace period minutes<input required type="number" min="0" value={attendanceRules.gracePeriodMinutes} onChange={(event) => setAttendanceRules({ ...attendanceRules, gracePeriodMinutes: Number(event.target.value) })} /></label>
          <label>Half-day threshold minutes<input required type="number" min="1" value={attendanceRules.halfDayThresholdMinutes} onChange={(event) => setAttendanceRules({ ...attendanceRules, halfDayThresholdMinutes: Number(event.target.value) })} /></label>
          <label>Overtime threshold minutes<input required type="number" min="0" value={attendanceRules.overtimeThresholdMinutes} onChange={(event) => setAttendanceRules({ ...attendanceRules, overtimeThresholdMinutes: Number(event.target.value) })} /></label>
          <label className="wide-field">Timezone<input required placeholder="e.g. Asia/Kolkata" value={attendanceRules.timeZoneId} onChange={(event) => setAttendanceRules({ ...attendanceRules, timeZoneId: event.target.value })} /></label>
        </div>
        <fieldset className="weekday-picker"><legend>Weekly holidays</legend>{['MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY', 'FRIDAY', 'SATURDAY', 'SUNDAY'].map((day) => <label key={day}>
          <input type="checkbox" checked={attendanceRules.weeklyHolidays.includes(day)} onChange={(event) => setAttendanceRules({
            ...attendanceRules,
            weeklyHolidays: event.target.checked
              ? [...attendanceRules.weeklyHolidays, day]
              : attendanceRules.weeklyHolidays.filter((value) => value !== day)
          })} />{day.slice(0, 3)}
        </label>)}</fieldset></section>
        <button className="primary-btn" disabled={isLoading}>{isLoading ? 'Saving…' : 'Save attendance rules'}</button>
      </form>}
    </>
  );

  const renderMyAttendance = () => {
    return <>
      <div className="page-heading"><div><h2>My attendance</h2><p>Check in or out and review your personal attendance history.</p></div>
        <div className="topbar-actions"><button className="primary-btn" disabled={isLoading || Boolean(todayAttendance?.checkInTime)} onClick={() => recordSelfAttendance('check-in')}>Check in</button>
          <button className="secondary-btn" disabled={isLoading || !todayAttendance?.checkInTime || Boolean(todayAttendance?.checkOutTime)} onClick={() => recordSelfAttendance('check-out')}>Check out</button></div>
      </div>
      {message && <p className="message" role="status">{message}</p>}
      <section className="panel-section">
        <div className="page-heading"><h3>Monthly summary</h3><label>Month<input type="month" value={attendanceMonth} onChange={(event) => setAttendanceMonth(event.target.value)} /></label><button className="secondary-btn" onClick={loadMyAttendance} disabled={isLoading}>Refresh</button></div>
        {renderAttendanceSummary(attendanceSummary)}
      </section>
      <section className="panel-section">
        <div className="page-heading"><h3>Attendance history</h3><form className="inline-filter" onSubmit={(event) => { event.preventDefault(); loadMyAttendance(); }}>
          <label>From<input type="date" value={historyRange.from} onChange={(event) => setHistoryRange({ ...historyRange, from: event.target.value })} /></label>
          <label>To<input type="date" value={historyRange.to} onChange={(event) => setHistoryRange({ ...historyRange, to: event.target.value })} /></label>
          <button className="secondary-btn" disabled={isLoading}>Apply</button>
        </form></div>
        {isLoading ? <p className="loading-state">Loading attendance…</p> : renderAttendanceTable(attendanceHistory)}
      </section>
    </>;
  };

  const renderLeaveApplications = (employeeSelfService) => (
    isLoading && leaveApplications.content.length === 0 ? <p className="loading-state">Loading leave applications…</p>
      : leaveApplications.content.length === 0 ? <div className="empty-state"><h3>No leave applications</h3><p>Applications matching this view will appear here.</p></div>
        : <div className="table-wrap">
          <table><thead><tr><th>Employee</th><th>Leave type</th><th>Dates</th><th>Days</th><th>Status</th><th>Actions</th></tr></thead>
            <tbody>{leaveApplications.content.map((application) => <tr key={application.id}>
              <td><strong>{employeeSelfService ? 'You' : application.employeeName}</strong>{!employeeSelfService && <small>{application.employeeNumber}</small>}</td>
              <td>{application.leaveType}{application.paid ? ' · Paid' : ' · Unpaid'}</td>
              <td>{application.startDate}{application.endDate !== application.startDate ? ` – ${application.endDate}` : ''}{application.halfDay ? <small>{application.halfDayPeriod} half-day</small> : null}</td>
              <td>{application.days}</td>
              <td><span className={`status-pill ${application.status.toLowerCase()}`}>{application.status.replaceAll('_', ' ')}</span>
                {application.reviewComment && <small>{application.reviewComment}</small>}</td>
              <td className="row-actions">
                {application.supportingDocumentAvailable && <button className="table-btn" disabled={isLoading} onClick={() => downloadLeaveDocument(application, employeeSelfService)}>Document</button>}
                {employeeSelfService && application.status === 'PENDING' && <button className="table-btn" disabled={isLoading} onClick={() => changeLeaveRequest(application, 'withdraw')}>Withdraw</button>}
                {employeeSelfService && application.status === 'APPROVED' && <button className="table-btn" disabled={isLoading} onClick={() => changeLeaveRequest(application, 'cancellation')}>Request cancellation</button>}
                {!employeeSelfService && application.status === 'PENDING' && <>
                  <button className="table-btn" disabled={isLoading} onClick={() => decideLeaveApplication(application, true)}>Approve</button>
                  <button className="table-btn" disabled={isLoading} onClick={() => decideLeaveApplication(application, false)}>Reject</button>
                </>}
                {!employeeSelfService && application.status === 'CANCELLATION_PENDING' && <>
                  <button className="table-btn" disabled={isLoading} onClick={() => decideLeaveApplication(application, true)}>Approve cancel</button>
                  <button className="table-btn" disabled={isLoading} onClick={() => decideLeaveApplication(application, false)}>Keep leave</button>
                </>}
              </td>
            </tr>)}</tbody>
          </table>
        </div>
  );

  const renderEmployeeLeave = () => (
    <>
      <div className="page-heading"><div><h2>My leave</h2><p>Apply for time off, review your leave balance, and track decisions.</p></div>
        <label>Balance year<input type="number" min="2000" max="2200" value={leaveYear} onChange={(event) => setLeaveYear(event.target.value)} /></label>
      </div>
      {message && <p className="message" role="alert">{message}</p>}
      <section className="panel-section">
        <div className="page-heading"><h3>Leave balances</h3><button className="secondary-btn" disabled={isLoading} onClick={loadEmployeeLeave}>Refresh</button></div>
        {leaveBalances.length === 0 ? <p className="empty-inline">No balances have been allocated for {leaveYear}.</p> : <div className="table-wrap">
          <table><thead><tr><th>Leave type</th><th>Entitlement</th><th>Carry forward</th><th>Used</th><th>Pending</th><th>Available</th><th>LOP</th></tr></thead>
            <tbody>{leaveBalances.map((balance) => <tr key={`${balance.leaveTypeId}-${balance.leaveYear}`}>
              <td>{balance.leaveTypeName}</td><td>{balance.annualEntitlement}</td><td>{balance.carryForward}</td>
              <td>{balance.used}</td><td>{balance.pending}</td><td>{balance.available}</td><td>{balance.lopDays}</td>
            </tr>)}</tbody>
          </table>
        </div>}
      </section>
      <section className="panel-section">
        <h3>Apply for leave</h3>
        {leaveTypes.filter((type) => type.active).length === 0 ? <p className="empty-inline">Your company has not configured any active leave types.</p> : <form className="employee-form leave-form" onSubmit={submitLeaveApplication}>
          <div className="form-grid">
            <label>Leave type<select required value={leaveForm.leaveTypeId} onChange={(event) => setLeaveForm({ ...leaveForm, leaveTypeId: event.target.value })}>
              <option value="">Choose type</option>{leaveTypes.filter((type) => type.active).map((type) => <option key={type.id} value={type.id}>{type.name} · {type.paid ? 'Paid' : 'Unpaid'}</option>)}
            </select></label>
            <label>Start date<input required type="date" value={leaveForm.startDate} onChange={(event) => setLeaveForm({ ...leaveForm, startDate: event.target.value, endDate: leaveForm.halfDay ? event.target.value : leaveForm.endDate })} /></label>
            {!leaveForm.halfDay && <label>End date<input required type="date" min={leaveForm.startDate || undefined} value={leaveForm.endDate} onChange={(event) => setLeaveForm({ ...leaveForm, endDate: event.target.value })} /></label>}
            <label>Reason<input required maxLength="2000" value={leaveForm.reason} onChange={(event) => setLeaveForm({ ...leaveForm, reason: event.target.value })} /></label>
            <label className="check-label"><input type="checkbox" checked={leaveForm.halfDay} onChange={(event) => setLeaveForm({ ...leaveForm, halfDay: event.target.checked, endDate: event.target.checked ? leaveForm.startDate : leaveForm.endDate, halfDayPeriod: event.target.checked ? 'MORNING' : '' })} />Half-day request</label>
            {leaveForm.halfDay && <label>Half-day period<select required value={leaveForm.halfDayPeriod} onChange={(event) => setLeaveForm({ ...leaveForm, halfDayPeriod: event.target.value })}><option value="MORNING">Morning</option><option value="AFTERNOON">Afternoon</option></select></label>}
            <label>Supporting document <span className="optional-label">PDF, PNG or JPEG · up to 5 MB</span><input type="file" accept=".pdf,.png,.jpg,.jpeg,application/pdf,image/png,image/jpeg" onChange={(event) => setLeaveForm({ ...leaveForm, document: event.target.files?.[0] || null })} /></label>
          </div>
          <button className="primary-btn" disabled={isLoading || !leaveForm.leaveTypeId}>{isLoading ? 'Submitting…' : 'Submit application'}</button>
        </form>}
      </section>
      <section className="panel-section">
        <div className="page-heading"><h3>My applications</h3><button className="secondary-btn" disabled={isLoading} onClick={loadEmployeeLeave}>Refresh</button></div>
        {renderLeaveApplications(true)}
      </section>
    </>
  );

  const renderCompanyLeave = () => (
    <>
      <div className="page-heading"><div><h2>Leave management</h2><p>Configure company leave, allocate balances, and review requests.</p></div>
        <button className="secondary-btn" disabled={isLoading} onClick={() => loadCompanyLeave(leaveApplications.number)}>Refresh</button>
      </div>
      {message && <p className="message" role="alert">{message}</p>}
      {leaveOverview && <div className="dashboard-grid leave-overview">
        <Stat label="Pending applications" value={leaveOverview.pendingApplications} />
        <Stat label="Approved applications" value={leaveOverview.approvedApplications} />
        <Stat label="Rejected applications" value={leaveOverview.rejectedApplications} />
        <Stat label="Cancellation requests" value={leaveOverview.cancellationRequests} />
        <Stat label="Active leave types" value={leaveOverview.activeLeaveTypes} />
        <Stat label="Active holidays" value={leaveOverview.activeCompanyHolidays} />
      </div>}
      <section className="panel-section">
        <h3>Create leave type</h3>
        <form className="employee-form leave-form" onSubmit={submitLeaveType}>
          <div className="form-grid">
            <label>Code<input required maxLength="24" value={leaveTypeForm.code} onChange={(event) => setLeaveTypeForm({ ...leaveTypeForm, code: event.target.value })} /></label>
            <label>Name<input required maxLength="120" value={leaveTypeForm.name} onChange={(event) => setLeaveTypeForm({ ...leaveTypeForm, name: event.target.value })} /></label>
            <label>Annual entitlement<input required type="number" min="0" step="0.5" value={leaveTypeForm.annualEntitlement} onChange={(event) => setLeaveTypeForm({ ...leaveTypeForm, annualEntitlement: event.target.value })} /></label>
            <label>Carry-forward limit<input required type="number" min="0" step="0.5" value={leaveTypeForm.carryForwardLimit} onChange={(event) => setLeaveTypeForm({ ...leaveTypeForm, carryForwardLimit: event.target.value })} /></label>
            <label>Maximum consecutive days<input required type="number" min="1" max="366" value={leaveTypeForm.maxConsecutiveDays} onChange={(event) => setLeaveTypeForm({ ...leaveTypeForm, maxConsecutiveDays: event.target.value })} /></label>
            {[
              ['paid', 'Paid leave'], ['supportingDocumentRequired', 'Require supporting document'],
              ['allowPastApplications', 'Allow past-date applications'], ['allowHalfDay', 'Allow half-day requests'],
              ['allowNegativeBalance', 'Allow negative balances']
            ].map(([key, label]) => <label className="check-label" key={key}><input type="checkbox" checked={leaveTypeForm[key]} onChange={(event) => setLeaveTypeForm({ ...leaveTypeForm, [key]: event.target.checked })} />{label}</label>)}
          </div>
          <button className="primary-btn" disabled={isLoading}>{isLoading ? 'Saving…' : 'Create leave type'}</button>
        </form>
        <div className="table-wrap"><table><thead><tr><th>Type</th><th>Paid</th><th>Entitlement</th><th>Status</th><th>Action</th></tr></thead>
          <tbody>{leaveTypes.map((type) => <tr key={type.id}><td>{type.name} <small>{type.code}</small></td><td>{type.paid ? 'Yes' : 'No'}</td><td>{type.annualEntitlement}</td><td>{type.active ? 'Active' : 'Inactive'}</td><td><button className="table-btn" disabled={isLoading} onClick={() => toggleLeaveType(type)}>{type.active ? 'Deactivate' : 'Activate'}</button></td></tr>)}</tbody>
        </table></div>
      </section>
      <section className="panel-section">
        <h3>Allocate employee balance</h3>
        <form className="employee-form leave-form" onSubmit={submitLeaveAllocation}>
          <div className="form-grid">
            <label>Employee<select required value={leaveAllocationForm.employeeId} onChange={(event) => setLeaveAllocationForm({ ...leaveAllocationForm, employeeId: event.target.value })}><option value="">Choose employee</option>{leaveEmployees.map((employee) => <option key={employee.id} value={employee.id}>{employee.firstName} {employee.lastName} · {employee.employeeNumber}</option>)}</select></label>
            <label>Leave type<select required value={leaveAllocationForm.leaveTypeId} onChange={(event) => setLeaveAllocationForm({ ...leaveAllocationForm, leaveTypeId: event.target.value })}><option value="">Choose type</option>{leaveTypes.map((type) => <option key={type.id} value={type.id}>{type.name}</option>)}</select></label>
            <label>Year<input required type="number" min="2000" max="2200" value={leaveAllocationForm.leaveYear} onChange={(event) => setLeaveAllocationForm({ ...leaveAllocationForm, leaveYear: event.target.value })} /></label>
            <label>Opening balance<input required type="number" min="0" step="0.5" value={leaveAllocationForm.openingBalance} onChange={(event) => setLeaveAllocationForm({ ...leaveAllocationForm, openingBalance: event.target.value })} /></label>
            <label>Annual entitlement<input type="number" min="0" step="0.5" placeholder="Use leave type default" value={leaveAllocationForm.annualEntitlement} onChange={(event) => setLeaveAllocationForm({ ...leaveAllocationForm, annualEntitlement: event.target.value })} /></label>
            <label>Carry forward<input type="number" min="0" step="0.5" value={leaveAllocationForm.carryForward} onChange={(event) => setLeaveAllocationForm({ ...leaveAllocationForm, carryForward: event.target.value })} /></label>
            <label>Additional accrual<input type="number" min="0" step="0.5" value={leaveAllocationForm.accrued} onChange={(event) => setLeaveAllocationForm({ ...leaveAllocationForm, accrued: event.target.value })} /></label>
            <label>Comment<input maxLength="500" value={leaveAllocationForm.comment} onChange={(event) => setLeaveAllocationForm({ ...leaveAllocationForm, comment: event.target.value })} /></label>
          </div>
          <button className="primary-btn" disabled={isLoading || !leaveAllocationForm.employeeId || !leaveAllocationForm.leaveTypeId}>Save allocation</button>
        </form>
      </section>
      <section className="panel-section">
        <h3>Company holidays</h3>
        <form className="org-form" onSubmit={submitCompanyHoliday}>
          <label>Name<input required maxLength="120" value={holidayForm.name} onChange={(event) => setHolidayForm({ ...holidayForm, name: event.target.value })} /></label>
          <label>Date<input required type="date" value={holidayForm.date} onChange={(event) => setHolidayForm({ ...holidayForm, date: event.target.value })} /></label>
          <label>Description<input maxLength="500" value={holidayForm.description} onChange={(event) => setHolidayForm({ ...holidayForm, description: event.target.value })} /></label>
          <button className="primary-btn" disabled={isLoading}>Add holiday</button>
        </form>
        <div className="table-wrap"><table><thead><tr><th>Date</th><th>Holiday</th><th>Description</th><th>Status</th><th>Action</th></tr></thead>
          <tbody>{leaveHolidays.map((holiday) => <tr key={holiday.id}><td>{holiday.date}</td><td>{holiday.name}</td><td>{holiday.description || '—'}</td><td>{holiday.active ? 'Active' : 'Inactive'}</td><td><button className="table-btn" disabled={isLoading} onClick={() => toggleCompanyHoliday(holiday)}>{holiday.active ? 'Deactivate' : 'Activate'}</button></td></tr>)}</tbody>
        </table></div>
      </section>
      <section className="panel-section">
        <h3>Applications and approvals</h3>
        <form className="filter-bar leave-filter" onSubmit={(event) => { event.preventDefault(); loadCompanyLeave(0); }}>
          <label>Search employee<input placeholder="Name or employee number" value={leaveFilters.search} onChange={(event) => setLeaveFilters({ ...leaveFilters, search: event.target.value })} /></label>
          <label>Employee<select value={leaveFilters.employeeId} onChange={(event) => setLeaveFilters({ ...leaveFilters, employeeId: event.target.value })}><option value="">All employees</option>{leaveEmployees.map((employee) => <option key={employee.id} value={employee.id}>{employee.firstName} {employee.lastName}</option>)}</select></label>
          <label>Status<select value={leaveFilters.status} onChange={(event) => setLeaveFilters({ ...leaveFilters, status: event.target.value })}><option value="">All statuses</option>{['PENDING', 'APPROVED', 'REJECTED', 'WITHDRAWN', 'CANCELLATION_PENDING', 'CANCELLED'].map((status) => <option key={status}>{status}</option>)}</select></label>
          <label>From<input type="date" value={leaveFilters.from} onChange={(event) => setLeaveFilters({ ...leaveFilters, from: event.target.value })} /></label>
          <label>To<input type="date" value={leaveFilters.to} onChange={(event) => setLeaveFilters({ ...leaveFilters, to: event.target.value })} /></label>
          <button className="secondary-btn" disabled={isLoading}>Filter</button>
        </form>
        {isLoading ? <p className="loading-state">Loading leave applications…</p> : renderLeaveApplications(false)}
        <div className="pagination"><span>{leaveApplications.totalElements} application(s)</span><div>
          <button className="secondary-btn" disabled={isLoading || leaveApplications.number <= 0} onClick={() => loadCompanyLeave(leaveApplications.number - 1)}>Previous</button>
          <span>Page {leaveApplications.totalPages ? leaveApplications.number + 1 : 0} of {leaveApplications.totalPages}</span>
          <button className="secondary-btn" disabled={isLoading || leaveApplications.number + 1 >= leaveApplications.totalPages} onClick={() => loadCompanyLeave(leaveApplications.number + 1)}>Next</button>
        </div></div>
      </section>
    </>
  );

  const renderSalaryManagement = () => {
    const selectedComponent = salaryComponents.find((item) => String(item.id) === salaryLineForm.componentId);
    return <>
      <div className="page-heading"><div><h2>Salary structures</h2><p>Configure company salary definitions and effective-dated employee assignments. Values are illustrative only; this does not process payroll or calculate tax.</p></div></div>
      {message && <p className="message" role="alert">{message}</p>}
      <section className="panel-section">
        <h3>{editingSalaryComponentId ? 'Edit salary component' : 'Create salary component'}</h3>
        <form className="employee-form" onSubmit={submitSalaryComponent}>
          <div className="form-grid">
            <label>Code<input required maxLength="32" value={salaryComponentForm.code} onChange={(event) => setSalaryComponentForm({ ...salaryComponentForm, code: event.target.value })} /></label>
            <label>Name<input required maxLength="120" value={salaryComponentForm.name} onChange={(event) => setSalaryComponentForm({ ...salaryComponentForm, name: event.target.value })} /></label>
            <label>Type<select value={salaryComponentForm.type} onChange={(event) => setSalaryComponentForm({ ...salaryComponentForm, type: event.target.value })}><option value="EARNING">Earning</option><option value="DEDUCTION">Deduction</option></select></label>
            <label>Calculation method<select value={salaryComponentForm.calculationMethod} onChange={(event) => setSalaryComponentForm({ ...salaryComponentForm, calculationMethod: event.target.value })}><option value="FIXED_AMOUNT">Fixed amount</option><option value="PERCENTAGE">Percentage</option></select></label>
            <label>Effective from<input required type="date" value={salaryComponentForm.effectiveFrom} onChange={(event) => setSalaryComponentForm({ ...salaryComponentForm, effectiveFrom: event.target.value })} /></label>
            <label>Effective to (optional)<input type="date" value={salaryComponentForm.effectiveTo} onChange={(event) => setSalaryComponentForm({ ...salaryComponentForm, effectiveTo: event.target.value })} /></label>
            <label className="checkbox-field"><input type="checkbox" checked={salaryComponentForm.taxable} onChange={(event) => setSalaryComponentForm({ ...salaryComponentForm, taxable: event.target.checked })} /> Taxable</label>
          </div>
          <p className="helper-text">Statutory components and rates must be configured by your company; no statutory rates are assumed.</p>
          <div className="form-actions"><button className="primary-btn" disabled={isLoading}>{editingSalaryComponentId ? 'Save component' : 'Create component'}</button>{editingSalaryComponentId && <button type="button" className="secondary-btn" onClick={() => { setEditingSalaryComponentId(null); setSalaryComponentForm({ code: '', name: '', type: 'EARNING', calculationMethod: 'FIXED_AMOUNT', taxable: true, effectiveFrom: today, effectiveTo: '' }); }}>Cancel edit</button>}</div>
        </form>
        <div className="table-wrap"><table><thead><tr><th>Component</th><th>Type</th><th>Method</th><th>Taxable</th><th>Effective</th><th>Status</th><th>Actions</th></tr></thead><tbody>
          {salaryComponents.map((component) => <tr key={component.id}><td><strong>{component.name}</strong><small>{component.code}</small></td><td>{component.type}</td><td>{component.calculationMethod.replace('_', ' ')}</td><td>{component.taxable ? 'Yes' : 'No'}</td><td>{component.effectiveFrom} – {component.effectiveTo || 'open'}</td><td>{component.active ? 'Active' : 'Inactive'}</td><td className="row-actions"><button className="table-btn" onClick={() => editSalaryComponent(component)}>Edit</button><button className="table-btn" disabled={isLoading} onClick={() => toggleSalaryStatus('components', component)}>{component.active ? 'Deactivate' : 'Activate'}</button></td></tr>)}
        </tbody></table></div>
        {salaryComponents.length === 0 && <p className="empty-inline">No salary components configured.</p>}
      </section>

      <section className="panel-section">
        <h3>{editingSalaryStructureId ? 'Edit salary structure' : 'Create salary structure'}</h3>
        <form className="employee-form" onSubmit={submitSalaryStructure}>
          <div className="form-grid">
            <label>Code<input required maxLength="32" value={salaryStructureForm.code} onChange={(event) => setSalaryStructureForm({ ...salaryStructureForm, code: event.target.value })} /></label>
            <label>Name<input required maxLength="120" value={salaryStructureForm.name} onChange={(event) => setSalaryStructureForm({ ...salaryStructureForm, name: event.target.value })} /></label>
            <label>Effective from<input required type="date" value={salaryStructureForm.effectiveFrom} onChange={(event) => setSalaryStructureForm({ ...salaryStructureForm, effectiveFrom: event.target.value })} /></label>
            <label>Effective to (optional)<input type="date" value={salaryStructureForm.effectiveTo} onChange={(event) => setSalaryStructureForm({ ...salaryStructureForm, effectiveTo: event.target.value })} /></label>
          </div>
          <div className="salary-line-editor">
            <label>Component<select value={salaryLineForm.componentId} onChange={(event) => setSalaryLineForm({ ...salaryLineForm, componentId: event.target.value, percentageBaseComponentId: '' })}><option value="">Choose component</option>{salaryComponents.filter((item) => item.active).map((item) => <option key={item.id} value={item.id}>{item.name} · {item.type} · {item.calculationMethod}</option>)}</select></label>
            <label>{selectedComponent?.calculationMethod === 'PERCENTAGE' ? 'Rate (%)' : 'Amount'}<input required type="number" min="0" step={selectedComponent?.calculationMethod === 'PERCENTAGE' ? '0.0001' : '0.01'} value={salaryLineForm.value} onChange={(event) => setSalaryLineForm({ ...salaryLineForm, value: event.target.value })} /></label>
            {selectedComponent?.calculationMethod === 'PERCENTAGE' && <label>Percentage base<select required value={salaryLineForm.percentageBaseComponentId} onChange={(event) => setSalaryLineForm({ ...salaryLineForm, percentageBaseComponentId: event.target.value })}><option value="">Choose earning component</option>{salaryComponents.filter((item) => item.active && item.type === 'EARNING' && salaryStructureForm.components.some((line) => String(line.componentId) === String(item.id))).map((item) => <option key={item.id} value={item.id}>{item.name}</option>)}</select></label>}
            <button type="button" className="secondary-btn" onClick={addSalaryStructureLine}>Add component</button>
          </div>
          <div className="table-wrap"><table><thead><tr><th>Component</th><th>Value</th><th>Base</th><th></th></tr></thead><tbody>
            {salaryStructureForm.components.map((line, index) => {
              const component = salaryComponents.find((item) => String(item.id) === String(line.componentId));
              const base = salaryComponents.find((item) => String(item.id) === String(line.percentageBaseComponentId));
              return <tr key={`${line.componentId}-${index}`}><td>{component?.name || 'Component'} · {component?.type}</td><td>{line.value}{component?.calculationMethod === 'PERCENTAGE' ? '%' : ''}</td><td>{base?.name || '—'}</td><td><button type="button" className="table-btn" onClick={() => setSalaryStructureForm((current) => ({ ...current, components: current.components.filter((_, itemIndex) => itemIndex !== index) }))}>Remove</button></td></tr>;
            })}
          </tbody></table></div>
          {salaryStructureForm.components.length === 0 && <p className="empty-inline">Add one or more active components. Percentage bases must be earning components in this structure.</p>}
          <div className="form-actions"><button className="primary-btn" disabled={isLoading || salaryStructureForm.components.length === 0}>{editingSalaryStructureId ? 'Save structure' : 'Create structure'}</button>{editingSalaryStructureId && <button type="button" className="secondary-btn" onClick={() => { setEditingSalaryStructureId(null); setSalaryStructureForm({ code: '', name: '', effectiveFrom: today, effectiveTo: '', components: [] }); }}>Cancel edit</button>}</div>
        </form>
        <div className="table-wrap"><table><thead><tr><th>Structure</th><th>Effective</th><th>Earnings</th><th>Deductions</th><th>Illustrative net</th><th>Breakdown</th><th>Status</th><th>Actions</th></tr></thead><tbody>
          {salaryStructures.map((structure) => <tr key={structure.id}><td><strong>{structure.name}</strong><small>{structure.code}</small></td><td>{structure.effectiveFrom} – {structure.effectiveTo || 'open'}</td><td>{structure.grossEarnings}</td><td>{structure.totalDeductions}</td><td>{structure.illustrativeNet}</td><td>{structure.components.map((item) => <small key={item.componentId}>{item.name}: {item.calculatedAmount}{item.percentageBaseCode ? ` (${item.value}% of ${item.percentageBaseCode})` : ''}</small>)}</td><td>{structure.active ? 'Active' : 'Inactive'}</td><td className="row-actions"><button className="table-btn" onClick={() => editSalaryStructure(structure)}>Edit</button><button className="table-btn" disabled={isLoading} onClick={() => toggleSalaryStatus('structures', structure)}>{structure.active ? 'Deactivate' : 'Activate'}</button></td></tr>)}
        </tbody></table></div>
        {salaryStructures.length === 0 && <p className="empty-inline">No salary structures configured.</p>}
      </section>

      <section className="panel-section">
        <h3>Assign structure and view history</h3>
        <form className="employee-form" onSubmit={submitSalaryAssignment}>
          <div className="form-grid">
            <label>Employee<select required value={salaryAssignmentForm.employeeId} onChange={(event) => { setSalaryAssignmentForm({ ...salaryAssignmentForm, employeeId: event.target.value }); setSalaryEmployeeId(event.target.value); }}><option value="">Choose employee</option>{salaryEmployees.map((employee) => <option key={employee.id} value={employee.id}>{employee.firstName} {employee.lastName} · {employee.employeeNumber}</option>)}</select></label>
            <label>Active structure<select required value={salaryAssignmentForm.salaryStructureId} onChange={(event) => setSalaryAssignmentForm({ ...salaryAssignmentForm, salaryStructureId: event.target.value })}><option value="">Choose structure</option>{salaryStructures.filter((structure) => structure.active).map((structure) => <option key={structure.id} value={structure.id}>{structure.name} · {structure.code}</option>)}</select></label>
            <label>Effective from<input required type="date" value={salaryAssignmentForm.effectiveFrom} onChange={(event) => setSalaryAssignmentForm({ ...salaryAssignmentForm, effectiveFrom: event.target.value })} /></label>
            <label>Effective to (optional)<input type="date" value={salaryAssignmentForm.effectiveTo} onChange={(event) => setSalaryAssignmentForm({ ...salaryAssignmentForm, effectiveTo: event.target.value })} /></label>
          </div>
          <button className="primary-btn" disabled={isLoading || !salaryEmployees.length || !salaryStructures.some((item) => item.active)}>Assign structure</button>
        </form>
        <label className="salary-history-select">History for employee<select value={salaryEmployeeId} onChange={(event) => setSalaryEmployeeId(event.target.value)}><option value="">Choose employee</option>{salaryEmployees.map((employee) => <option key={employee.id} value={employee.id}>{employee.firstName} {employee.lastName} · {employee.employeeNumber}</option>)}</select></label>
        {salaryHistory.length === 0 ? <p className="empty-inline">No assignments for this employee.</p> : <div className="table-wrap"><table><thead><tr><th>Structure</th><th>Effective period</th><th>Earnings</th><th>Deductions</th><th>Illustrative net</th><th>Component snapshot</th></tr></thead><tbody>
          {salaryHistory.map((assignment) => <tr key={assignment.id}><td>{assignment.structureName}<small>{assignment.structureCode}</small></td><td>{assignment.effectiveFrom} – {assignment.effectiveTo || 'open'}</td><td>{assignment.grossEarnings}</td><td>{assignment.totalDeductions}</td><td>{assignment.illustrativeNet}</td><td>{assignment.components.map((item) => <small key={item.code}>{item.name}: {item.calculatedAmount}</small>)}</td></tr>)}
        </tbody></table></div>}
      </section>
    </>;
  };

  const renderEmployeeSalary = () => {
    const todayAssignment = employeeSalaryHistory.find((assignment) =>
      assignment.active && assignment.effectiveFrom <= today && (!assignment.effectiveTo || assignment.effectiveTo >= today));
    return <>
      <div className="page-heading"><div><h2>My salary</h2><p>Current and historical assigned salary structure details.</p></div></div>
      {message && <p className="message" role="alert">{message}</p>}
      {isLoading ? <p className="loading-state">Loading salary information…</p> : !employeeSalaryHistory.length ? <div className="empty-state"><h3>No salary assignments</h3><p>Your company has not assigned a salary structure yet.</p></div> : <>
        {todayAssignment && <section className="panel-section"><h3>Current assignment · {todayAssignment.structureName}</h3><div className="dashboard-grid"><Stat label="Earnings" value={todayAssignment.grossEarnings} /><Stat label="Deductions" value={todayAssignment.totalDeductions} /><Stat label="Illustrative net" value={todayAssignment.illustrativeNet} /></div><p className="helper-text">Illustrative values only. This is not a payslip or payroll calculation.</p></section>}
        <section className="panel-section"><h3>Assignment history</h3><div className="table-wrap"><table><thead><tr><th>Structure</th><th>Effective period</th><th>Earnings</th><th>Deductions</th><th>Illustrative net</th><th>Components</th></tr></thead><tbody>
          {employeeSalaryHistory.map((assignment) => <tr key={assignment.id}><td>{assignment.structureName}<small>{assignment.structureCode}</small></td><td>{assignment.effectiveFrom} – {assignment.effectiveTo || 'open'}</td><td>{assignment.grossEarnings}</td><td>{assignment.totalDeductions}</td><td>{assignment.illustrativeNet}</td><td>{assignment.components.map((item) => <small key={item.code}>{item.name}: {item.calculatedAmount}</small>)}</td></tr>)}
        </tbody></table></div></section>
      </>}
    </>;
  };

  const renderPlatformSalary = () => (
    <>
      <div className="page-heading"><div><h2>Salary platform summary</h2><p>Read-only cross-company counts. Individual salary values are not exposed.</p></div></div>
      {message && <p className="message" role="alert">{message}</p>}
      {isLoading ? <p className="loading-state">Loading salary summary…</p> : platformSalarySummary && <>
        <div className="dashboard-grid"><Stat label="Companies" value={platformSalarySummary.companies} /><Stat label="Active structures" value={platformSalarySummary.activeStructures} /><Stat label="Active assignments" value={platformSalarySummary.activeAssignments} /></div>
        <section className="panel-section"><h3>Company counts</h3><div className="table-wrap"><table><thead><tr><th>Company</th><th>Code</th><th>Active structures</th><th>Active assignments</th></tr></thead><tbody>
          {platformSalarySummary.companySummaries.map((company) => <tr key={company.companyId}><td>{company.companyName}</td><td>{company.companyCode}</td><td>{company.activeStructures}</td><td>{company.activeAssignments}</td></tr>)}
        </tbody></table></div></section>
      </>}
    </>
  );

  const renderCompanyRegistration = () => (
    <>
      <div className="page-heading"><div><h2>Register company</h2><p>Create a tenant and its initial company administrator.</p></div><button className="secondary-btn" onClick={() => setView('super-admin')}>Back to platform</button></div>
      <form className="employee-form" onSubmit={handleCompanyRegistration}>
        <div className="form-grid">
          {[
            ['companyName', 'Company name'], ['companyCode', 'Company code'], ['companyEmail', 'Company email'],
            ['companyPhone', 'Phone'], ['companyAddress', 'Address'], ['subscriptionPlan', 'Subscription plan'],
            ['adminUsername', 'Admin username'], ['adminPassword', 'Admin password']
          ].map(([key, label]) => <label key={key}>{label}<input required type={key === 'companyEmail' ? 'email' : key === 'adminPassword' ? 'password' : 'text'} value={companyForm[key]} onChange={(event) => setCompanyForm({ ...companyForm, [key]: event.target.value })} /></label>)}
        </div>
        {message && <p className="message" role="alert">{message}</p>}
        <button className="primary-btn" disabled={isLoading}>{isLoading ? 'Registering…' : 'Register company'}</button>
      </form>
    </>
  );

  const renderSuperAdmin = () => (
    <>
      <div className="page-heading"><div><h2>Platform overview</h2><p>Company workforce summaries are read-only in this module.</p></div><div className="topbar-actions"><button className="secondary-btn" onClick={() => setView('salary-platform')}>Salary summary</button><button className="secondary-btn" onClick={() => setView('platform-payroll')}>Payroll oversight</button><button className="secondary-btn" onClick={() => setView('platform-payslips')}>Payslip oversight</button><button className="secondary-btn" onClick={() => setView('company-registration')}>Register company</button><button className="secondary-btn" onClick={handleLogout}>Logout</button></div></div>
      {message && <p className="message" role="alert">{message}</p>}
      {stats ? <div className="dashboard-grid">
        <Stat label="Companies" value={stats.totalCompanies} /><Stat label="Employees" value={stats.totalEmployees} /><Stat label="Active companies" value={stats.activeCompanies} /><Stat label="Inactive companies" value={stats.inactiveCompanies} />
      </div> : <p className="loading-state">Loading platform metrics…</p>}
      <section className="panel-section"><h3>Companies</h3>{companies.length === 0 ? <p className="empty-inline">No companies registered.</p> : <div className="table-wrap"><table><thead><tr><th>Company</th><th>Code</th><th>Status</th><th>Employees</th></tr></thead><tbody>      {companies.map((company) => <tr key={company.id}><td>{company.name}</td><td>{company.code}</td><td>{company.status}</td><td className="row-actions"><button className="table-btn" onClick={() => viewPlatformEmployees(company)}>View employees</button><button className="table-btn" disabled={isLoading} onClick={() => viewPlatformLeave(company)}>Leave overview</button></td></tr>)}</tbody></table></div>}</section>
      {platformCompany && <section className="panel-section"><div className="page-heading"><h3>{platformCompany.name} employees</h3><button className="link-btn" onClick={() => setPlatformCompany(null)}>Close</button></div>{isLoading ? <p className="loading-state">Loading…</p> : platformEmployees.length === 0 ? <p className="empty-inline">No employees in this company.</p> : <div className="table-wrap"><table><thead><tr><th>Name</th><th>Employee number</th><th>Department</th><th>Status</th></tr></thead><tbody>{platformEmployees.map((employee) => <tr key={employee.id}><td>{employee.firstName} {employee.lastName}</td><td>{employee.employeeNumber}</td><td>{employee.departmentName}</td><td>{employee.status}</td></tr>)}</tbody></table></div>}</section>}
      {platformLeaveOverview && <section className="panel-section"><div className="page-heading"><div><h3>{companies.find((company) => company.id === platformLeaveOverview.companyId)?.name || 'Company'} leave overview</h3><p>Read-only company-scoped leave metrics.</p></div><button className="link-btn" onClick={() => setPlatformLeaveOverview(null)}>Close</button></div>
        <div className="dashboard-grid leave-overview"><Stat label="Pending applications" value={platformLeaveOverview.pendingApplications} /><Stat label="Approved applications" value={platformLeaveOverview.approvedApplications} /><Stat label="Rejected applications" value={platformLeaveOverview.rejectedApplications} /><Stat label="Cancellation requests" value={platformLeaveOverview.cancellationRequests} /><Stat label="Active leave types" value={platformLeaveOverview.activeLeaveTypes} /><Stat label="Active holidays" value={platformLeaveOverview.activeCompanyHolidays} /></div>
      </section>}
      <section className="panel-section">
        <div className="page-heading"><div><h3>Platform attendance oversight</h3><p>Read-only, company-scoped attendance view.</p></div></div>
        <form className="filter-bar" onSubmit={loadPlatformAttendance}>
          <label>Company<select required value={platformAttendanceCompanyId} onChange={(event) => setPlatformAttendanceCompanyId(event.target.value)}><option value="">Choose company</option>{companies.map((company) => <option key={company.id} value={company.id}>{company.name}</option>)}</select></label>
          <label>Date<input required type="date" value={platformAttendanceDate} onChange={(event) => setPlatformAttendanceDate(event.target.value)} /></label>
          <button className="secondary-btn" disabled={isLoading}>View attendance</button>
        </form>
        {renderAttendanceTable(platformAttendance)}
      </section>
    </>
  );

  if (!token || !user) {
    if (view === 'forgot-password') return <div className="app-shell auth-only">{renderForgotPassword()}</div>;
    if (view === 'reset-password') return <div className="app-shell auth-only">{renderResetPassword()}</div>;
    return <div className="app-shell">{renderLogin()}</div>;
  }

  if (user.role === 'SUPER_ADMIN') {
    return <div className="app-shell dashboard-shell">{view === 'company-registration'
      ? renderCompanyRegistration()
      : view === 'salary-platform'
        ? <><div className="page-heading"><button className="secondary-btn" onClick={() => setView('super-admin')}>Back to platform</button></div>{renderPlatformSalary()}</>
        : view === 'platform-payroll'
          ? <><div className="page-heading"><button className="secondary-btn" onClick={() => setView('super-admin')}>Back to platform</button></div><PayrollProcessing api={api} role={user.role} /></>
          : view === 'platform-payslips'
            ? <><div className="page-heading"><button className="secondary-btn" onClick={() => setView('super-admin')}>Back to platform</button></div><Payslips api={api} role={user.role} companies={companies} /></>
        : renderSuperAdmin()}</div>;
  }

  if (user.role === 'EMPLOYEE') {
    return <div className="app-shell dashboard-shell">
      <header className="topbar"><div><span className="eyebrow">Employee self-service</span><h1>{view === 'my-profile' ? 'My profile' : view === 'my-leave' ? 'My leave' : view === 'my-salary' ? 'My salary' : view === 'my-payroll' ? 'My payroll' : view === 'my-payslips' ? 'My payslips' : 'My attendance'}</h1></div><div className="topbar-actions"><button className="secondary-btn" onClick={() => setView('my-attendance')}>Attendance</button><button className="secondary-btn" onClick={() => setView('my-leave')}>My leave</button><button className="secondary-btn" onClick={() => setView('my-salary')}>My salary</button><button className="secondary-btn" onClick={() => setView('my-payroll')}>My payroll</button><button className="secondary-btn" onClick={() => setView('my-payslips')}>My payslips</button><button className="secondary-btn" onClick={() => setView('my-profile')}>Profile</button><button className="secondary-btn" onClick={handleLogout}>Logout</button></div></header>
      {view === 'my-payroll' ? <PayrollProcessing api={api} role={user.role} /> : view === 'my-payslips' ? <Payslips api={api} role={user.role} /> : view === 'my-profile' ? (isLoading ? <p className="loading-state">Loading profile…</p> : selectedEmployee ? renderEmployeeDetails(selectedEmployee) : <p className="empty-state">No employee profile is linked to this account.</p>) : view === 'my-leave' ? renderEmployeeLeave() : view === 'my-salary' ? renderEmployeeSalary() : renderMyAttendance()}
      {message && <p className="message" role="alert">{message}</p>}
    </div>;
  }

  return <div className="app-shell dashboard-shell">
    {renderHeader()}
    {renderCompanyNavigation()}
    <main className="workspace-content">
      {view === 'employees' && renderEmployeeList()}
      {view === 'employee-form' && renderEmployeeForm()}
      {view === 'employee-details' && selectedEmployee && renderEmployeeDetails(selectedEmployee)}
      {(view === 'departments' || view === 'designations') && renderOrgManagement()}
      {view === 'attendance' && renderAttendanceWorkspace()}
      {view === 'attendance-form' && renderAttendanceForm()}
      {view === 'attendance-rules' && renderAttendanceRules()}
      {view === 'leave-management' && renderCompanyLeave()}
      {view === 'salary-management' && renderSalaryManagement()}
      {view === 'payroll' && <PayrollProcessing api={api} role={user.role} />}
      {view === 'payslips' && <Payslips api={api} role={user.role} />}
      {!['employees', 'employee-form', 'employee-details', 'departments', 'designations', 'attendance', 'attendance-form', 'attendance-rules', 'leave-management', 'salary-management', 'payroll', 'payslips'].includes(view) && renderEmployeeList()}
    </main>
  </div>;
}

function ProfileField({ label, value }) {
  return <div className="profile-field"><span>{label}</span><strong>{value || '—'}</strong></div>;
}

function Stat({ label, value }) {
  return <div className="stat-card"><span>{label}</span><strong>{value ?? 0}</strong></div>;
}

export default App;
