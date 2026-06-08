// Source code is decompiled from a .class file using FernFlower decompiler (from Intellij IDEA).
package br.gov.detrandf.patrimonio;

import java.io.IOException;
import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import javax.naming.Context;
import javax.naming.InitialContext;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.sql.DataSource;

@WebServlet({"/api/patrimonio/*"})
public class PatrimonioServlet extends HttpServlet {
   private static final String DATASOURCE_JNDI = "java:comp/env/jdbc/patrimonio";
   private static final String SQL = "SELECT   patrimonio,   descricao,   situacao_fisica,   status,   unidade,   endereco,   observacao FROM patrimonio.vw_site_consulta_patrimonio WHERE patrimonio = ?";

   public PatrimonioServlet() {
   }

   protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
      String pathInfo = req.getPathInfo();
      if (pathInfo != null && !pathInfo.equals("/")) {
         String numeroRaw = pathInfo.substring(1).trim();
         if (!numeroRaw.matches("\\d{1,15}")) {
            this.sendError(resp, 400, "Número de patrimônio inválido.");
         } else {
            DataSource ds;
            try {
               Context ctx = new InitialContext();
               ds = (DataSource)ctx.lookup("java:comp/env/jdbc/patrimonio");
            } catch (Exception e) {
               this.log("Erro ao obter DataSource: " + e.getMessage());
               this.sendError(resp, 503, "Serviço temporariamente indisponível.");
               return;
            }

            String[] tentativas = this.gerarTentativas(numeroRaw);

            try {
               Connection conn = ds.getConnection();
               Throwable var8 = null;

               try {
                  String resultado = null;

                  for(String tentativa : tentativas) {
                     PreparedStatement ps = conn.prepareStatement("SELECT   patrimonio,   descricao,   situacao_fisica,   status,   unidade,   endereco,   observacao FROM patrimonio.vw_site_consulta_patrimonio WHERE patrimonio = ?");
                     Throwable var15 = null;

                     try {
                        ps.setString(1, tentativa);
                        ResultSet rs = ps.executeQuery();
                        Throwable var17 = null;

                        try {
                           if (rs.next()) {
                              resultado = this.buildJson(rs);
                              break;
                           }
                        } catch (Throwable var73) {
                           var17 = var73;
                           throw var73;
                        } finally {
                           if (rs != null) {
                              if (var17 != null) {
                                 try {
                                    rs.close();
                                 } catch (Throwable var71) {
                                    var17.addSuppressed(var71);
                                 }
                              } else {
                                 rs.close();
                              }
                           }

                        }
                     } catch (Throwable var75) {
                        var15 = var75;
                        throw var75;
                     } finally {
                        if (ps != null) {
                           if (var15 != null) {
                              try {
                                 ps.close();
                              } catch (Throwable var70) {
                                 var15.addSuppressed(var70);
                              }
                           } else {
                              ps.close();
                           }
                        }

                     }
                  }

                  resp.setContentType("application/json;charset=UTF-8");
                  resp.setHeader("Cache-Control", "no-store");
                  PrintWriter out = resp.getWriter();
                  if (resultado != null) {
                     resp.setStatus(200);
                     out.print(resultado);
                  } else {
                     resp.setStatus(404);
                     out.print("{\"encontrado\":false}");
                  }
               } catch (Throwable var77) {
                  var8 = var77;
                  throw var77;
               } finally {
                  if (conn != null) {
                     if (var8 != null) {
                        try {
                           conn.close();
                        } catch (Throwable var69) {
                           var8.addSuppressed(var69);
                        }
                     } else {
                        conn.close();
                     }
                  }

               }
            } catch (SQLException e) {
               this.log("Erro SQL: " + e.getMessage());
               this.sendError(resp, 500, "Erro ao consultar o patrimônio.");
            }

         }
      } else {
         this.sendError(resp, 400, "Informe o número do patrimônio na URL.");
      }
   }

   private String[] gerarTentativas(String numero) {
      String base = numero.replaceFirst("^0+", "");
      if (base.isEmpty()) {
         base = "0";
      }

      return new String[]{numero, base, String.format("%06d", Long.parseLong(base)), String.format("%07d", Long.parseLong(base)), String.format("%08d", Long.parseLong(base))};
   }

   private String buildJson(ResultSet rs) throws SQLException {
      String obs = rs.getString("observacao");
      boolean temObs = obs != null && !obs.trim().isEmpty();
      StringBuilder sb = new StringBuilder();
      sb.append("{");
      sb.append("\"encontrado\":true,");
      sb.append("\"num\":").append(this.jsonStr(rs.getString("patrimonio"))).append(",");
      sb.append("\"desc\":").append(this.jsonStr(rs.getString("descricao"))).append(",");
      sb.append("\"sf\":").append(this.jsonStr(rs.getString("situacao_fisica"))).append(",");
      sb.append("\"status\":").append(this.jsonStr(rs.getString("status"))).append(",");
      sb.append("\"unidade\":").append(this.jsonStr(rs.getString("unidade"))).append(",");
      sb.append("\"end\":").append(this.jsonStr(rs.getString("endereco"))).append(",");
      if (temObs) {
         sb.append("\"obs\":{");
         sb.append("\"tipo\":\"aguardando\",");
         sb.append("\"texto\":").append(this.jsonStr(obs));
         sb.append("}");
      } else {
         sb.append("\"obs\":null");
      }

      sb.append("}");
      return sb.toString();
   }

   private String jsonStr(String value) {
      return value == null ? "null" : "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t") + "\"";
   }

   private void sendError(HttpServletResponse resp, int status, String msg) throws IOException {
      resp.setStatus(status);
      resp.setContentType("application/json;charset=UTF-8");
      resp.getWriter().print("{\"erro\":\"" + msg + "\"}");
   }
}
