using System;
using System.Collections.Generic;
using System.Linq;
using System.Net;
using System.Net.Http;
using System.Threading.Tasks;
using ESMS.Libraries.Common.Abstractions;
using Microsoft.AspNetCore.Http;
using Microsoft.Extensions.Configuration;
using Newtonsoft.Json;

namespace ESMS.Reports.PowerBI
{
    public class ReportServerReportsReader : IReportsReader
    {
        private readonly IHttpContextAccessor _httpContextAccessor;
        private readonly IConfigurationRoot _config;
        private readonly string _baseReportServerUrl;
        private readonly bool _ignoreCertErrors;
        private readonly string _cookieName;
        private readonly string _environment;

        public ReportServerReportsReader(IHttpContextAccessor httpContextAccessor, IConfigurationRoot config)
        {
            _httpContextAccessor = httpContextAccessor;
            _config = config;
            _baseReportServerUrl = _config["ReportServer:baseUrl"];
            _ignoreCertErrors = bool.TrueString.Equals(_config["ReportServer:ignoreCertErrors"], StringComparison.OrdinalIgnoreCase);
            _cookieName = _config["ReportServer:cookieName"];
            _environment = _config["ReportServer:environment"];
        }

        private class OdataBIReportsModel
        {
            public List<BIReportModel> value;
        }

        /// <summary>
        /// Gets a list of Power BI reports from the Report Server
        /// </summary>
        /// <returns></returns>
        public async Task<List<BIReportModel>> GetReports()
        {
            List<BIReportModel> ret = new List<BIReportModel>();

            if (_ignoreCertErrors)
                ServicePointManager.ServerCertificateValidationCallback = (o, b, j, t) => true;

            var httpClientHandler = new HttpClientHandler()
            {
                UseCookies = true,
                CookieContainer = new CookieContainer()
            };

            using (var httpClient = new HttpClient(httpClientHandler, true) { BaseAddress = new Uri(_baseReportServerUrl) })
            {
                await SetAuthCookie(_httpContextAccessor.HttpContext, httpClient, httpClientHandler,
                              _config["ReportServer:userName"], _config["ReportServer:password"]);

                var odataReportsString = await httpClient.GetStringAsync("reports/api/v2.0/PowerBIReports?$select=Name,Path");
                var odataModel = JsonConvert.DeserializeObject<OdataBIReportsModel>(odataReportsString);

                //reports folder structure is Home/[environment]/Domains/[domain]/...

                ret = odataModel.value
                    .Where(x => x.Path.IndexOf($"/{_environment}/", StringComparison.OrdinalIgnoreCase) > -1)
                    .ToList();

                ret.ForEach(x =>
                {
                    var segments = x.Path.Split('/');
                    var rootIdx = Array.IndexOf<string>(segments, "Domains");
                    x.RootFolder = (rootIdx > -1 && segments.Length > rootIdx + 1 ? segments[rootIdx + 1] : "/");
                    x.Path = $"{_baseReportServerUrl}/reports/powerbi{x.Path}?rs:embed=true";

                });
            }

            return ret;
        }

        private async Task SetAuthCookie(HttpContext httpContext, HttpClient httpClient, HttpClientHandler httpClientHandler, string userName, string password)
        {
            var content = new FormUrlEncodedContent(new List<KeyValuePair<string, string>>
                {
                    new KeyValuePair<string, string>("name", userName),
                    new KeyValuePair<string, string>("password", password),
                    new KeyValuePair<string, string>("env", _environment)
                });

            await httpClient.PostAsync("ReportServer/logon.aspx", content);
            foreach (Cookie cookie in httpClientHandler.CookieContainer.GetCookies(httpClient.BaseAddress))
            {
                if (cookie.Name == _cookieName)
                {
                    httpContext.Response.Cookies.Append(_cookieName, cookie.Value);
                    return;
                }
            }
        }
    }
}
