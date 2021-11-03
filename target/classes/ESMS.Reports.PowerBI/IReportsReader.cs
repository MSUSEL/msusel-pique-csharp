using System.Collections.Generic;
using System.Threading.Tasks;

namespace ESMS.Reports.PowerBI
{
    public interface IReportsReader
    {
        Task<List<BIReportModel>> GetReports();
        Task<List<BIReportModel>> GetReports();
    }
}
