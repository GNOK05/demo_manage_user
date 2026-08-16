import { Component, OnInit, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink } from '@angular/router';
import { AuthService } from '../../core/auth.service';
import { CompanyApiService } from '../../core/company-api.service';
import { Attendance } from '../../core/models';
@Component({standalone:true,imports:[CommonModule,RouterLink],templateUrl:'./attendance.component.html',styleUrl:'./attendance.component.scss'})
export class AttendanceComponent implements OnInit {entries=signal<(Attendance & {userName?:string})[]>([]);constructor(public auth:AuthService,private api:CompanyApiService){}title(){return this.auth.user()?.role==='ADMIN'?'Chấm công toàn công ty':this.auth.user()?.role==='MANAGER'?'Chấm công phòng ban':'Chấm công cá nhân';}ngOnInit(){const role=this.auth.user()?.role;const request=role==='ADMIN'?this.api.allAttendance():role==='MANAGER'?this.api.departmentAttendance():this.api.myAttendance();request.subscribe(x=>this.entries.set(x));}}
